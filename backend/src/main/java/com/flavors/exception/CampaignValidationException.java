package com.flavors.exception;

import java.util.Map;

/**
 * The campaign form has fields that are missing or wrong. Each entry maps a field name (as the form
 * sends it, e.g. {@code startDate} or {@code deliverables[0].count}) to the message to show beside it.
 */
public class CampaignValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public CampaignValidationException(Map<String, String> errors) {
        super("Correct the highlighted fields and try again.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
