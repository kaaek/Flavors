package com.flavors.dto;

/**
 * What Flavors asks the provider for: a hosted payment page for one attempt.
 *
 * @param reference   our reference for the attempt (payment_transaction.provider_ref); the provider echoes it back
 * @param invoice     a short text the payer sees
 * @param callbackUrl where the provider reports the result (a hint only: Flavors then asks for the status)
 * @param redirectUrl where the payer's browser goes after paying or failing
 */
public record HostedPaymentRequest(
        String reference,
        long amountCents,
        String currency,
        String invoice,
        String callbackUrl,
        String redirectUrl) {
}
