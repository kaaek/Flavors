package com.flavors.exception;
 
/** The campaign is already featured on the creator's portfolio. Maps to HTTP 409. */
public class AlreadyFeaturedException extends RuntimeException {
 
    public AlreadyFeaturedException() {
        super("This campaign is already featured on your portfolio.");
    }
}