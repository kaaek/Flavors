package com.flavors.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/brand/creators/{creatorId}/portfolio}: a creator's verified
 * portfolio as a brand sees it (UC-F1.1). Prices are integer cents.
 *
 * <p>It shows a campaign's redemptions and ROI, but never the bills or discounts behind them:
 * those belong to the brand that ran the campaign.
 */
public record CreatorPortfolioResponse(
        long creatorId,
        String displayName,
        String bio,
        String foodCategory,
        String location,
        long priceMinCents,
        long priceMaxCents,
        List<AccountView> socialAccounts,
        List<FeaturedCampaignView> featuredCampaigns,
        /** Null when she has no review yet. One decimal, e.g. 4.5. */
        BigDecimal averageRating,
        int reviewCount,
        List<ReviewView> reviews) {

    /**
     * The last figures pulled from a connected account, with the time of that pull. After a failed
     * pull the old figures stay, with their old time (FR-F1.9).
     */
    public record AccountView(String platform, Long followers, BigDecimal engagementRate, Instant lastPulledAt) {
    }

    /**
     * One of her featured campaigns. {@code verified} is true only for a completed campaign with at
     * least one tracked redemption (FR-F1.3). {@code roiPercent} is null without redemptions.
     */
    public record FeaturedCampaignView(
            long campaignId, String name, boolean verified, long redemptions, BigDecimal roiPercent) {
    }

    public record ReviewView(int rating, String text, Instant createdAt) {
    }
}
