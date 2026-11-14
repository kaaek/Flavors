package com.flavors.dto;

import com.flavors.enums.PaymentTransactionStatus;

/** What the hosted checkout page shows for one payment attempt. */
public record PaymentCheckout(
        Long campaignId,
        String campaignName,
        long amountCents,
        String currency,
        PaymentTransactionStatus status) {
}
