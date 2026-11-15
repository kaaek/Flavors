package com.flavors.dto;

import com.flavors.enums.PaymentTransactionStatus;

/** An opened payment attempt, as the idempotent start call stores it. Public so the stored JSON can be read back. */
public record PaymentAttempt(long transactionId, String reference, long amountCents, PaymentTransactionStatus status) {
}
