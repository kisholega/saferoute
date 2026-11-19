package com.saferoute.ai.exception;

/** The caller must wait before retrying (e.g. repeated failed logins). Mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
