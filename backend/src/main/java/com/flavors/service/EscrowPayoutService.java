package com.flavors.service;

/** Internal execution/reconciliation only; no caller-provided money or destination. */
public interface EscrowPayoutService {
    int processPendingPayouts();
    boolean processOperation(long operationId);
}
