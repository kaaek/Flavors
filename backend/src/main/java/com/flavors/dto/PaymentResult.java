package com.flavors.dto;

import com.flavors.enums.PaymentTransactionStatus;

/** Where a payment stands after a status check, and which campaign it is for. */
public record PaymentResult(Long campaignId, PaymentTransactionStatus status) {
}
