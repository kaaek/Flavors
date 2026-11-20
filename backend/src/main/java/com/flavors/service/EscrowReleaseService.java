package com.flavors.service;

/** Database-only release authorization and rejection freezing. No payout or dispute creation. */
public interface EscrowReleaseService {

    /**
     * Authorizes the latest approved proof under the campaign lock, joining the review transaction.
     * Returns true only when a new operation was created; an identical authorization returns false.
     * This is an internal service contract, not an endpoint accepting caller-provided approval claims.
     */
    boolean authorizeApproved(long campaignId, long submissionId, long reviewId);

    /** Rechecks Timer C under the campaign lock in an independent transaction; false if no longer eligible. */
    boolean authorizeOverdue(long campaignId);

    /** Joins the rejection transaction; true only on HELD -> FROZEN, false for an identical frozen retry. */
    boolean freezeForRejection(long campaignId, long submissionId, long reviewId);

    /** Recovers persisted approvals without an operation, with a separate transaction per candidate. */
    int recoverApprovedReviews();
}
