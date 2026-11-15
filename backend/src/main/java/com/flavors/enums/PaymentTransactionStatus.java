package com.flavors.enums;

/** One payment attempt: PENDING until the provider's answer is applied, then SUCCEEDED or FAILED. */
public enum PaymentTransactionStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
