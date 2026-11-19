package com.saferoute.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.saferoute.ai.exception.UnauthorizedException;
import com.saferoute.ai.model.RememberToken;
import com.saferoute.ai.model.User;
import com.saferoute.ai.repository.RememberTokenRepository;
import com.saferoute.ai.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Answers "who is using the app right now?".
 * <p>
 * The signed-in user lives in the HTTP session. Sessions are lost when the server restarts, so sign-in
 * also sets a 30-day "remember me" cookie: when a request arrives without a session but with a valid
 * cookie, the session is quietly re-created. Every service that touches private data asks this class.
 */
@Service
public class CurrentUserService {

    public static final String SESSION_KEY = "saferoute.userId";
    public static final String REMEMBER_COOKIE = "SAFEROUTE_REMEMBER";
    static final Duration REMEMBER_FOR = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final RememberTokenRepository tokenRepository;
    private final Clock clock;

    public CurrentUserService(UserRepository userRepository, RememberTokenRepository tokenRepository, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    /** @throws UnauthorizedException when nobody is signed in */
    public Long currentUserId() {
        return optionalUserId().orElseThrow(() -> new UnauthorizedException("Please sign in to continue."));
    }

    public User currentUser() {
        return userRepository.findById(currentUserId())
                .orElseThrow(() -> new UnauthorizedException("Your account no longer exists. Please sign in again."));
    }

    public Optional<Long> optionalUserId() {
        HttpServletRequest request = currentRequest();
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_KEY) instanceof Long id) {
            return Optional.of(id);
        }
        return restoreFromCookie(request);
    }

    /** Starts a fresh session (new id, preventing session fixation) and sets the remember-me cookie. */
    public void signIn(Long userId) {
        HttpServletRequest request = currentRequest();
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true).setAttribute(SESSION_KEY, userId);

        String token = Tokens.random(32);
        Instant now = clock.instant();
        tokenRepository.save(new RememberToken(userId, hash(token), now, now.plus(REMEMBER_FOR)));
        tokenRepository.deleteExpired(now);
        writeCookie(token, REMEMBER_FOR);
    }

    public void signOut() {
        HttpServletRequest request = currentRequest();
        cookieValue(request).ifPresent(token -> tokenRepository.deleteByTokenHash(hash(token)));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        writeCookie("", Duration.ZERO);
    }

    private Optional<Long> restoreFromCookie(HttpServletRequest request) {
        Optional<String> token = cookieValue(request);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<RememberToken> stored = tokenRepository.findByTokenHash(hash(token.get()));
        if (stored.isEmpty() || stored.get().getExpiresAt().isBefore(clock.instant())
                || userRepository.findById(stored.get().getUserId()).isEmpty()) {
            return Optional.empty();
        }
        Long userId = stored.get().getUserId();
        request.getSession(true).setAttribute(SESSION_KEY, userId);
        return Optional.of(userId);
    }

    private static Optional<String> cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (REMEMBER_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void writeCookie(String value, Duration maxAge) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(REMEMBER_COOKIE, value)
                .httpOnly(true)
                .secure(attributes.getRequest().isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new UnauthorizedException("No web request in progress.");
    }
}
