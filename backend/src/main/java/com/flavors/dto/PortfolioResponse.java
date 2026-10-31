package com.flavors.dto;
 
import com.flavors.enums.SocialAccountStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
 
/**
 * Response of {@code GET} and {@code PUT /api/creator/portfolio}: what Lara sees on the Manage
 * Portfolio page (FR-F1.7). Prices are integer cents.
 */
public record PortfolioResponse(
        String displayName,
        String bio,
        String foodCategory,
        String location,
        long priceMinCents,
        long priceMaxCents,
        List<SocialAccountView> socialAccounts,
        List<CampaignView> completedCampaigns) {
 
    /** A connected account with its figures and 'last updated' time (the token is never sent). */
    public record SocialAccountView(
            String platform,
            SocialAccountStatus status,
            Long followers,
            BigDecimal engagementRate,
            Instant lastPulledAt) {
    }
 
    /** One of her completed campaigns, and whether she features it on her portfolio. */
    public record CampaignView(long id, String name, boolean featured) {
    }
}