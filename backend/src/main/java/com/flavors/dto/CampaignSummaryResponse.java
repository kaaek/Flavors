package com.flavors.dto;

import com.flavors.enums.CampaignStatus;
import java.time.Instant;

/** A row of the brand's campaign list. {@code creatorName} is null for a draft with no creator yet. */
public record CampaignSummaryResponse(
        Long id,
        String name,
        CampaignStatus status,
        Long creatorId,
        String creatorName,
        Long priceOfferedCents,
        Instant updatedAt) {
}
