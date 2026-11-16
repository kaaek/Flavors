package com.flavors.exception;

/**
 * The campaign is active or over, so its offer can no longer be set up or changed: diners may already hold QR codes for
 * it. Maps to HTTP 409.
 */
public class OfferLockedException extends RuntimeException {

    public OfferLockedException() {
        super("The offer can't be set up or changed once the campaign is active or over.");
    }
}
