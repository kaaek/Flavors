package com.flavors.exception;

/** No payment has this reference, or the campaign has no escrow yet. The web layer answers 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException() {
        super("Payment not found.");
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
