package com.saferoute.ai.exception;

/** A business rule failed for one specific input field, so the UI can show the message next to that field. */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
