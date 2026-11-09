package com.flavors.dto;

import com.flavors.enums.CampaignStatus;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One of the brand's campaigns in full. Money is integer cents; {@code creator} is null until one is chosen. */
public record CampaignResponse(
        Long id,
        String name,
        CampaignStatus status,
        CreatorView creator,
        List<BranchResponse> branches,
        Long priceOfferedCents,
        LocalDate startDate,
        LocalDate lastPostingDate,
        List<DeliverableView> deliverables,
        String contentRequirements,
        Instant sentAt,
        /** Timer A: when the creator's time to answer runs out. */
        Instant responseDeadline,
        Instant createdAt,
        Instant updatedAt) {

    public record CreatorView(Long id, String displayName) {
    }

    public record DeliverableView(DeliverablePlatform platform, DeliverableType type, int count) {
    }
}
