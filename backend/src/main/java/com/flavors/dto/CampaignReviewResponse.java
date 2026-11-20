
package com.flavors.dto;

import com.flavors.entity.Review;
import java.time.Instant;

public record CampaignReviewResponse(
        Long id,
        Long campaignId,
        short rating,
        String text,
        Instant createdAt
) {
    public static CampaignReviewResponse from(Review review) {
        return new CampaignReviewResponse(
                review.getId(),
                review.getCampaignId(),
                review.getRating(),
                review.getText(),
                review.getCreatedAt()
        );
    }
}
