package com.flavors.dto;

import com.flavors.enums.CounterOfferStatus;
import java.time.Instant;
import java.util.List;

public record CounterOfferResponse(
        Long id,
        Long campaignId,
        Long priceCents,
        List<DeliverableItem> deliverables,
        CounterOfferStatus status,
        Instant createdAt,
        Instant decidedAt) {
}
