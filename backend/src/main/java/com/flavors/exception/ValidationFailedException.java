package com.flavors.exception;

/** A rule that spans several fields (or the database) failed; the message belongs next to one field. */
public class ValidationFailedException extends RuntimeException {

    private final String field;

    public ValidationFailedException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
