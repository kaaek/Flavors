package com.flavors.enums;

/** What the payment provider says about a payment when Flavors asks (the answer is authoritative, callbacks are not). */
public enum ProviderPaymentStatus {
    /** Paid: the provider confirmed the money was collected. */
    PAID,
    /** Not decided yet (the customer has not finished paying). */
    PENDING,
    /** The payment link expired or the payment was refused. */
    FAILED
}
