package com.flavors.dto;

import com.flavors.enums.PaymentTransactionStatus;

/**
 * Where a payment stands after a status check, which campaign it is for, and whether its money is held in escrow. A
 * payment recorded after Timer B is SUCCEEDED but not held.
 */
public record PaymentResult(Long campaignId, PaymentTransactionStatus status, boolean held) {
}
