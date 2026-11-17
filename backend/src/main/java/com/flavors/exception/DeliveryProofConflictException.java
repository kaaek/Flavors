package com.flavors.exception;

/** The campaign state or current round no longer permits this edit/submission. */
public class DeliveryProofConflictException extends RuntimeException {

    public DeliveryProofConflictException(String message) {
        super(message);
    }
}
