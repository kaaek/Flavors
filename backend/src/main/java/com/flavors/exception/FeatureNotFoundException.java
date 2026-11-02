package com.flavors.exception;
 
/** The campaign is not featured on the creator's portfolio, so there is nothing to remove. Maps to HTTP 404. */
public class FeatureNotFoundException extends RuntimeException {
 
    public FeatureNotFoundException() {
        super("This campaign is not featured on your portfolio.");
    }
}