package com.saferoute.ai.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.saferoute.ai.dto.EmergencyContactRequest;
import com.saferoute.ai.model.EmergencyContact;
import com.saferoute.ai.repository.EmergencyContactRepository;

@Service
public class EmergencyContactService {

    private final EmergencyContactRepository emergencyContactRepository;

    public EmergencyContactService(EmergencyContactRepository emergencyContactRepository) {
        this.emergencyContactRepository = emergencyContactRepository;
    }

    public List<EmergencyContact> getContactsByUser(Long userId) {
        return emergencyContactRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(EmergencyContact::getCreatedAt).reversed())
                .toList();
    }

    public EmergencyContact saveContact(EmergencyContactRequest request) {
        validateRequest(request);

        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(request.getUserId());
        contact.setName(request.getName().trim());
        contact.setPhone(normalizePhone(request.getPhone()));
        contact.setRelation(request.getRelation().trim());
        contact.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        return emergencyContactRepository.save(contact);
    }

    public EmergencyContact updateContact(Long contactId, EmergencyContactRequest request) {
        validateRequest(request);

        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency contact not found with id: " + contactId));
        contact.setName(request.getName().trim());
        contact.setPhone(normalizePhone(request.getPhone()));
        contact.setRelation(request.getRelation().trim());
        contact.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        return emergencyContactRepository.save(contact);
    }

    public void deleteContact(Long contactId) {
        if (!emergencyContactRepository.existsById(contactId)) {
            throw new IllegalArgumentException("Emergency contact not found with id: " + contactId);
        }
        emergencyContactRepository.deleteById(contactId);
    }

    private void validateRequest(EmergencyContactRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Emergency contact request is required.");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Contact name is required.");
        }
        if (request.getName().trim().length() < 2) {
            throw new IllegalArgumentException("Contact name must be at least 2 characters long.");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        String normalizedPhone = normalizePhone(request.getPhone());
        if (normalizedPhone.length() < 8 || !normalizedPhone.matches("[+0-9]+")) {
            throw new IllegalArgumentException("Enter a valid phone number with at least 8 digits.");
        }
        if (request.getRelation() == null || request.getRelation().isBlank()) {
            throw new IllegalArgumentException("Relationship is required.");
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("[^+\\d]", "");
        return digits.isEmpty() ? phone.trim() : digits;
    }
}
