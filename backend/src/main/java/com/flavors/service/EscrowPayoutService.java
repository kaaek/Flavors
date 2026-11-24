
package com.flavors.service;

/** Internal payout execution and reconciliation. */
public interface EscrowPayoutService {

    int processPendingPayouts();

    boolean processOperation(long operationId);

    /** Records administrator evidence without moving money. */
    void recordManualReviewEvidence(
            long operationId,
            String evidenceReference,
            String finding);
}
