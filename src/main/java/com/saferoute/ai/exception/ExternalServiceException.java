package com.saferoute.ai.exception;

/** An outside service (geocoder, router) could not be reached or returned an error. Mapped to HTTP 503. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
