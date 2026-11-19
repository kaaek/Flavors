
package com.flavors.dto;

/**
 * Represents the provider's payout result.
 *
 * FAILED is definitive.
 * safeToRetry means the provider guarantees no transfer occurred.
 * NOT_FOUND means the provider authoritatively confirmed
 * that no payout exists for the operation key.
 */
public record PayoutResult(
        Outcome outcome,
        PayoutRequest identity,
        String providerRef,
        boolean safeToRetry) {

    public enum Outcome {
        PENDING,
        CONFIRMED,
        FAILED,
        UNKNOWN,
        NOT_FOUND
    }

    public PayoutResult {
        if (outcome == null || identity == null
                || (safeToRetry && outcome != Outcome.FAILED)
                || ((outcome == Outcome.PENDING
                    || outcome == Outcome.CONFIRMED)
                    && (providerRef == null
                        || providerRef.isBlank()
                        || providerRef.length() > 100))
                || (outcome == Outcome.NOT_FOUND
                    && providerRef != null)) {

            throw new IllegalArgumentException(
                    "Invalid provider outcome.");
        }
    }

    public static PayoutResult unknown(PayoutRequest request) {
        return new PayoutResult(
                Outcome.UNKNOWN, request, null, false);
    }

    public static PayoutResult notFound(PayoutRequest request) {
        return new PayoutResult(
                Outcome.NOT_FOUND, request, null, false);
    }
}
