package com.saferoute.ai.exception;

/** A link that existed has expired (e.g. a tracking link after the trip ended). Mapped to HTTP 410. */
public class GoneException extends RuntimeException {

    public GoneException(String message) {
        super(message);
    }
}
