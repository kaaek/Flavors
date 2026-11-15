package com.flavors.exception;

/**
 * The brand pressed Pay again, but the provider says the earlier attempt was already paid. The payment is held
 * through the normal path and no second checkout is opened. The web layer answers 409.
 */
public class PaymentAlreadyPaidException extends RuntimeException {

    public PaymentAlreadyPaidException() {
        super("This campaign is already paid. The payment is held in escrow.");
    }
}
