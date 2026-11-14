package com.flavors.dto;

import com.flavors.enums.EscrowStatus;
import java.time.Instant;

/** The escrow of one campaign as the brand and the creator see it (FR-F7.7). Money is integer cents. */
public record EscrowResponse(
        Long campaignId,
        EscrowStatus status,
        long amountCents,
        Instant heldAt,
        Instant releasedAt,
        Instant updatedAt) {
}
