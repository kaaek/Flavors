package com.flavors.exception;

/** Also used for another user's proof, so ownership is never disclosed. */
public class DeliveryProofNotFoundException extends RuntimeException {

    public DeliveryProofNotFoundException() {
        super("Delivery proof was not found.");
    }
}
