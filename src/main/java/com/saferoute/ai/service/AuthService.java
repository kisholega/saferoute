package com.saferoute.ai.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saferoute.ai.dto.AuthDtos.LoginRequest;
import com.saferoute.ai.dto.AuthDtos.ProfileRequest;
import com.saferoute.ai.dto.AuthDtos.RegisterRequest;
import com.saferoute.ai.exception.FieldValidationException;
import com.saferoute.ai.exception.TooManyRequestsException;
import com.saferoute.ai.exception.UnauthorizedException;
import com.saferoute.ai.model.User;
import com.saferoute.ai.repository.UserRepository;

/**
 * Accounts: registration, sign-in and profile updates.
 * Passwords are stored only as BCrypt hashes. After {@value #MAX_FAILURES} wrong passwords an email
 * address is locked for {@value #LOCK_SECONDS} seconds to slow down password guessing.
 */
@Service
@Transactional
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final int LOCK_SECONDS = 60;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PhoneNumbers phoneNumbers;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                       PhoneNumbers phoneNumbers, CurrentUserService currentUserService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumbers = phoneNumbers;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new FieldValidationException("email", "An account with this email already exists. Sign in instead.");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phoneNumbers.normalize(request.phone(), "phone"));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        currentUserService.signIn(saved.getId());
        return saved;
    }

    public User login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Attempts current = attempts.get(email);
        Instant now = clock.instant();
        if (current != null && current.lockedUntil != null && now.isBefore(current.lockedUntil)) {
            long wait = Math.max(1, current.lockedUntil.getEpochSecond() - now.getEpochSecond());
            throw new TooManyRequestsException("Too many wrong attempts. Try again in " + wait + " seconds.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailure(email, now);
            throw new UnauthorizedException("Email or password is incorrect.");
        }
        attempts.remove(email);
        currentUserService.signIn(user.getId());
        return user;
    }

    public void logout() {
        currentUserService.signOut();
    }

    public User updateProfile(ProfileRequest request) {
        User user = currentUserService.currentUser();
        user.setName(request.name().trim());
        user.setPhone(phoneNumbers.normalize(request.phone(), "phone"));
        return user;
    }

    private void recordFailure(String email, Instant now) {
        attempts.compute(email, (key, existing) -> {
            Attempts next = existing == null || (existing.lockedUntil != null && now.isAfter(existing.lockedUntil))
                    ? new Attempts() : existing;
            next.failures++;
            if (next.failures >= MAX_FAILURES) {
                next.lockedUntil = now.plusSeconds(LOCK_SECONDS);
                next.failures = 0;
            }
            return next;
        });
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        int failures;
        Instant lockedUntil;
    }
}
