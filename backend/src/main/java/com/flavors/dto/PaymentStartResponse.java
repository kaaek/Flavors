package com.flavors.dto;

/** The brand's Pay button: where to send the payer. */
public record PaymentStartResponse(String checkoutUrl) {
}
