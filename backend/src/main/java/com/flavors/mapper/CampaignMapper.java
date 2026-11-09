package com.flavors.mapper;

import com.flavors.dto.BranchResponse;
import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CampaignSummaryResponse;
import com.flavors.entity.Branch;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts a campaign (with its creator and branches, which the service has loaded) to API responses. */
@Component
public class CampaignMapper {

    /** @param creator null while the draft has no creator */
    public CampaignResponse toResponse(Campaign campaign, CreatorProfile creator, List<Branch> branches) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                creator == null ? null : new CampaignResponse.CreatorView(creator.getUserId(), creator.getDisplayName()),
                branches.stream()
                        .sorted(Comparator.comparing(Branch::getId))
                        .map(b -> new BranchResponse(b.getId(), b.getName(), b.getLocation()))
                        .toList(),
                campaign.getPriceOfferedCents(),
                campaign.getStartDate(),
                campaign.getLastPostingDate(),
                campaign.getDeliverables().stream()
                        .map(d -> new CampaignResponse.DeliverableView(d.getPlatform(), d.getType(), d.getCount()))
                        .toList(),
                campaign.getContentRequirements(),
                campaign.getSentAt(),
                campaign.getResponseDeadline(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }

    /** @param creator null while the draft has no creator */
    public CampaignSummaryResponse toSummary(Campaign campaign, CreatorProfile creator) {
        return new CampaignSummaryResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getCreatorId(),
                creator == null ? null : creator.getDisplayName(),
                campaign.getPriceOfferedCents(),
                campaign.getUpdatedAt());
    }
}
