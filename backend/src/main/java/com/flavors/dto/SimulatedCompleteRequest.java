package com.flavors.dto;

import com.flavors.enums.ProviderPaymentStatus;

/** The mock checkout's button: PAID (the payer paid) or FAILED (the card was refused). */
public record SimulatedCompleteRequest(ProviderPaymentStatus outcome) {
}
