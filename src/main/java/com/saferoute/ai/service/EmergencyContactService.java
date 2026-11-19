package com.saferoute.ai.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.EmergencyContactRequest;
import com.saferoute.ai.exception.FieldValidationException;
import com.saferoute.ai.exception.NotFoundException;
import com.saferoute.ai.model.EmergencyContact;
import com.saferoute.ai.repository.EmergencyContactRepository;

/**
 * Emergency contacts of the signed-in user.
 * <p>
 * Rules: phone numbers are stored in E.164 format, a number can only be saved once, there is a
 * maximum number of contacts, and there is always exactly one primary contact while the list is
 * non-empty. Every contact gets a private push topic when created.
 */
@Service
@Transactional
public class EmergencyContactService {

    private final EmergencyContactRepository repository;
    private final CurrentUserService currentUserService;
    private final PhoneNumbers phoneNumbers;
    private final SafeRouteProperties properties;

    public EmergencyContactService(EmergencyContactRepository repository, CurrentUserService currentUserService,
                                   PhoneNumbers phoneNumbers, SafeRouteProperties properties) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.phoneNumbers = phoneNumbers;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<EmergencyContact> listContacts() {
        return listFor(currentUserService.currentUserId());
    }

    /** Used by background jobs (alerts, trip watchdog) that know the user id but have no session. */
    @Transactional(readOnly = true)
    public List<EmergencyContact> listFor(Long userId) {
        return repository.findByUserIdOrderByIsPrimaryDescCreatedAtAsc(userId);
    }

    public EmergencyContact findOwned(Long id) {
        return repository.findByIdAndUserId(id, currentUserService.currentUserId())
                .orElseThrow(() -> new NotFoundException("Emergency contact not found."));
    }

    public EmergencyContact create(EmergencyContactRequest request) {
        Long userId = currentUserService.currentUserId();
        List<EmergencyContact> existing = listFor(userId);
        int max = properties.contacts().maxPerUser();
        if (existing.size() >= max) {
            throw new IllegalArgumentException("You can save up to " + max + " emergency contacts. Remove one to add another.");
        }

        String phone = phoneNumbers.normalize(request.phone(), "phone");
        ensureUniquePhone(existing, phone, null);

        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setPushTopic(Tokens.pushTopic());
        applyFields(contact, request, phone);

        boolean makePrimary = existing.isEmpty() || Boolean.TRUE.equals(request.isPrimary());
        if (makePrimary) {
            existing.forEach(other -> other.setIsPrimary(false));
        }
        contact.setIsPrimary(makePrimary);
        return repository.save(contact);
    }

    public EmergencyContact update(Long id, EmergencyContactRequest request) {
        EmergencyContact contact = findOwned(id);
        List<EmergencyContact> all = listFor(contact.getUserId());

        String phone = phoneNumbers.normalize(request.phone(), "phone");
        ensureUniquePhone(all, phone, id);
        applyFields(contact, request, phone);
        if (contact.getPushTopic() == null) {
            contact.setPushTopic(Tokens.pushTopic());
        }

        if (Boolean.TRUE.equals(request.isPrimary()) && !Boolean.TRUE.equals(contact.getIsPrimary())) {
            all.forEach(other -> other.setIsPrimary(false));
            contact.setIsPrimary(true);
        }
        return contact;
    }

    public void delete(Long id) {
        EmergencyContact contact = findOwned(id);
        repository.delete(contact);
        repository.flush();

        if (Boolean.TRUE.equals(contact.getIsPrimary())) {
            listFor(contact.getUserId()).stream()
                    .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .ifPresent(oldest -> oldest.setIsPrimary(true));
        }
    }

    private void ensureUniquePhone(List<EmergencyContact> contacts, String phone, Long ignoreId) {
        boolean duplicate = contacts.stream()
                .anyMatch(other -> phone.equals(other.getPhone()) && !other.getId().equals(ignoreId));
        if (duplicate) {
            throw new FieldValidationException("phone", "This number is already saved as an emergency contact.");
        }
    }

    private static void applyFields(EmergencyContact contact, EmergencyContactRequest request, String phone) {
        contact.setName(request.name().trim());
        contact.setPhone(phone);
        contact.setRelation(request.relation().trim());
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        contact.setEmail(email.isEmpty() ? null : email);
    }
}
