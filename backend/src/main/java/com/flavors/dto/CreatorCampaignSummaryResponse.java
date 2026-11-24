package com.flavors.dto;

import com.flavors.enums.CampaignStatus;
import java.time.Instant;

public record CreatorCampaignSummaryResponse(
        Long id,
        String name,
        CampaignStatus status,
        Long brandId,
        String brandName,
        Long priceOfferedCents,
        Long agreedPriceCents,
        Instant paymentDeadline,
        Instant acceptedAt,
        Instant updatedAt) {
}