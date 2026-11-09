package com.flavors.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the brand's form sends to save a draft or to send a campaign (UC-F3.1). Every field may be missing:
 * how much is required depends on whether it is a draft or a send, so {@code CampaignServiceImpl} checks it
 * and answers with one message per field instead of Bean Validation's all-or-nothing annotations.
 *
 * <p>The platform and type are text so that an unknown value gets a message next to its row, not a parse error.
 *
 * @param priceOfferedCents the budget: what the brand offers the creator, in cents
 */
public record CampaignRequest(
        String name,
        Long creatorId,
        List<Long> branchIds,
        Long priceOfferedCents,
        LocalDate startDate,
        LocalDate lastPostingDate,
        List<DeliverableRequest> deliverables,
        String contentRequirements) {

    public record DeliverableRequest(String platform, String type, Integer count) {
    }
}
