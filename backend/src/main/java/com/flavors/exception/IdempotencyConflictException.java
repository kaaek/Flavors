package com.flavors.exception;

/** The same idempotency key was reused for a different request, so it must not return the stored result. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String scope, String key) {
        super("Idempotency key '" + key + "' (scope '" + scope + "') was already used for a different request");
    }
}
