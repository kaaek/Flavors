package com.flavors.platform.idempotency;

import java.util.function.Supplier;

/**
 * Makes a retried request run its action only once (NF-6): the Pay button, payment webhooks, and
 * release/refund/dispute decisions. State lives in the service's own {@code idempotency_key} table
 * ({@code db/templates/idempotency_key.sql}).
 */
public interface IdempotencyService {

    /**
     * Runs {@code action} at most once per (scope, key) and returns its result; a repeat of a completed
     * request returns the stored result without running the action again.
     *
     * <p>Everything happens in one transaction, including the action's own database work. If the
     * action throws, the key row is rolled back with it, so a later retry can run the action again.
     * The action must be a database-only change: an external call (e.g. to the payment provider) made
     * inside it cannot be rolled back.
     *
     * <p><b>Security:</b> keys come from clients, so {@code scope} must include the caller (user id or
     * tenant), e.g. {@code "pay:" + user.id()}. Otherwise one user can replay, and read the stored result
     * of, another user's request.
     *
     * @param requestFingerprint describes the request (e.g. its JSON body); reusing a key with a
     *                           different fingerprint throws {@link IdempotencyConflictException}
     * @param resultType         must be JSON-serializable, since the result is stored as jsonb
     */
    <T> T execute(String scope, String key, String requestFingerprint, Supplier<T> action, Class<T> resultType);
}
