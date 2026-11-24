package com.flavors.dto;

/** Internal provider identity. Amount is the immutable creator net, in cents. */
public record PayoutRequest(String operationKey, String destinationRef, long amountCents, String currency) {
    public PayoutRequest {
        if (operationKey == null || operationKey.isBlank() || destinationRef == null || destinationRef.isBlank()
                || amountCents <= 0 || !"USD".equals(currency)) {
            throw new IllegalArgumentException("A payout requires a stable identity, destination, positive USD amount.");
        }
    }
}
