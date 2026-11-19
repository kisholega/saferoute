package com.saferoute.ai.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Unguessable random tokens (URL-safe), used for tracking links and private push topics. */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** @param bytes amount of randomness; 16 bytes = 128 bits gives a 22-character token */
    public static String random(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** A private ntfy topic, e.g. "sr-k3J9xQ...". Only someone who knows it can subscribe. */
    public static String pushTopic() {
        return "sr-" + random(15);
    }
}
