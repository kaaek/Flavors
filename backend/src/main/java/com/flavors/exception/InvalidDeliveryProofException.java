package com.flavors.exception;

/** A link, screenshot or final-submission rule was not satisfied. */
public class InvalidDeliveryProofException extends RuntimeException {

    public InvalidDeliveryProofException(String message) {
        super(message);
    }
}
