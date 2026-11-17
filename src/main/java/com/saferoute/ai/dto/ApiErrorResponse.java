package com.saferoute.ai.dto;

import java.time.LocalDateTime;

public class ApiErrorResponse {
    private final String message;
    private final LocalDateTime timestamp;

    public ApiErrorResponse(String message) {
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
