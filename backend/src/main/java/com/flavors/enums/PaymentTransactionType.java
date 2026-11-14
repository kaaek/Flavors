package com.flavors.enums;

/** The kinds of money movement (payment_transaction.type, V5). EEC-44 writes CHARGE only. */
public enum PaymentTransactionType {
    CHARGE,
    RELEASE,
    REFUND
}
