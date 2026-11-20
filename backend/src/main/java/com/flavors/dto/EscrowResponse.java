package com.flavors.dto;

import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import java.time.Instant;

/** The escrow of one campaign as the brand and the creator see it (FR-F7.7). Money is integer cents. */
public record EscrowResponse(
        Long campaignId,
        EscrowStatus status,
        long amountCents,
        Instant heldAt,
        Instant releasedAt,
        Instant updatedAt,
        Long feeCents,
        Long creatorNetCents,
        PayoutStatus payoutStatus,
        ReleaseAuthorizationSource authorizationSource,
        Instant authorizedAt,
        Instant submittedAt,
        Instant confirmedAt,
        Boolean simulated,
        boolean payoutAvailable,
        boolean payoutNeedsReview) {

    /** Source compatibility for callers constructing the previous escrow-only response. */
    public EscrowResponse(Long campaignId, EscrowStatus status, long amountCents, Instant heldAt,
                          Instant releasedAt, Instant updatedAt) {
        this(campaignId, status, amountCents, heldAt, releasedAt, updatedAt,
                null, null, null, null, null, null, null, null, false, false);
    }
}
