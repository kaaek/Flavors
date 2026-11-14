package com.flavors.dto;

/** The page the payer is sent to: the provider's hosted payment page (card or wallet data never reach Flavors). */
public record HostedPaymentSession(String checkoutUrl) {
}
