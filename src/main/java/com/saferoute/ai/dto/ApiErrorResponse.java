package com.saferoute.ai.dto;

import java.time.Instant;
import java.util.Map;

/** Every API error has this shape, so the frontend can always read {@code message}. */
public record ApiErrorResponse(String message, Map<String, String> fieldErrors, Instant timestamp) {

    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(message, Map.of(), Instant.now());
    }

    public static ApiErrorResponse of(String message, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(message, fieldErrors, Instant.now());
    }
}
