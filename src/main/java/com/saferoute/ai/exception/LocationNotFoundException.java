package com.saferoute.ai.exception;

/** A place name could not be turned into coordinates. Mapped to HTTP 422. */
public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(String message) {
        super(message);
    }
}
