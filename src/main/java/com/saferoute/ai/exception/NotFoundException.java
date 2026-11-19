package com.saferoute.ai.exception;

/** A requested record does not exist (or does not belong to the current user). Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
