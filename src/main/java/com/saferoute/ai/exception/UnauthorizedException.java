package com.saferoute.ai.exception;

/** The user is not signed in. Mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
