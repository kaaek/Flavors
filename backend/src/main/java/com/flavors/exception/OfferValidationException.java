package com.flavors.exception;

import java.util.Map;

/**
 * The offer form has fields that are missing or wrong (EX-F10.1.3). Each entry maps a field name, as the form sends it,
 * to the message to show beside it. Nothing is saved. Maps to HTTP 400.
 */
public class OfferValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public OfferValidationException(Map<String, String> errors) {
        super("Correct the highlighted fields and try again.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
