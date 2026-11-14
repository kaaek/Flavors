package com.flavors.exception;

/**
 * The campaign is not in a state where this answer is possible (already answered, expired, past its deadline...).
 * The web layer turns it into a 409.
 */
public class CampaignResponseStateException extends RuntimeException {

    public CampaignResponseStateException(String message) {
        super(message);
    }
}
