package com.flavors.exception;

/**
 * The payment provider could not be reached, answered with an error, or payments are switched off. Nothing has
 * changed in Flavors; the caller can try again. The web layer answers 502 (or 503 when payments are off).
 */
public class PaymentProviderException extends RuntimeException {

    private final boolean disabled;

    public PaymentProviderException(String message) {
        this(message, false, null);
    }

    public PaymentProviderException(String message, Throwable cause) {
        this(message, false, cause);
    }

    private PaymentProviderException(String message, boolean disabled, Throwable cause) {
        super(message, cause);
        this.disabled = disabled;
    }

    /** Payments are not switched on in this environment (flavors.whish.mode=disabled). */
    public static PaymentProviderException disabled() {
        return new PaymentProviderException("Payments are not switched on for this environment.", true, null);
    }

    public boolean isDisabled() {
        return disabled;
    }
}
