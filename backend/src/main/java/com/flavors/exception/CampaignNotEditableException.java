package com.flavors.exception;

/** Only a draft can be changed or sent; after Send the terms are what the creator was shown. */
public class CampaignNotEditableException extends RuntimeException {

    public CampaignNotEditableException() {
        super("This campaign has already been sent, so it can't be changed or sent again.");
    }
}
