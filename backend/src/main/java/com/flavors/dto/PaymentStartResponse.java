package com.flavors.dto;

import com.flavors.enums.PaymentTransactionStatus;

/** The brand's Pay button: where to send the payer, and the amount in USD cents. */
public record PaymentStartResponse(
        String checkoutUrl,
        long amountCents,
        String currency,
        PaymentTransactionStatus status) {
}
