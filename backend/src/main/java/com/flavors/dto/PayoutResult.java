package com.flavors.dto;

/** FAILED is definitive; safeToRetry means the provider guarantees no transfer occurred. */
public record PayoutResult(Outcome outcome, PayoutRequest identity, String providerRef, boolean safeToRetry) {
    public enum Outcome { PENDING, CONFIRMED, FAILED, UNKNOWN }

    public PayoutResult {
        if (outcome == null || identity == null || (safeToRetry && outcome != Outcome.FAILED)
                || ((outcome == Outcome.PENDING || outcome == Outcome.CONFIRMED)
                    && (providerRef == null || providerRef.isBlank() || providerRef.length() > 100))) {
            throw new IllegalArgumentException("Invalid provider outcome.");
        }
    }

    public static PayoutResult unknown(PayoutRequest request) {
        return new PayoutResult(Outcome.UNKNOWN, request, null, false);
    }
}
