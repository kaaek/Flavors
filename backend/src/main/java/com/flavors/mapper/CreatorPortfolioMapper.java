package com.flavors.mapper;

import com.flavors.dto.CreatorPortfolioResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.Review;
import com.flavors.entity.SocialAccount;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts a creator's profile, accounts, featured campaigns and reviews into the brand's portfolio view. */
@Component
public class CreatorPortfolioMapper {

    public CreatorPortfolioResponse.FeaturedCampaignView toFeatured(
            Campaign campaign, long redemptions, boolean verified, BigDecimal roiPercent) {
        return new CreatorPortfolioResponse.FeaturedCampaignView(
                campaign.getId(), campaign.getName(), verified, redemptions, roiPercent);
    }

    public CreatorPortfolioResponse toResponse(
            CreatorProfile profile,
            List<SocialAccount> accounts,
            List<CreatorPortfolioResponse.FeaturedCampaignView> featuredCampaigns,
            List<Review> reviews,
            BigDecimal averageRating) {
        return new CreatorPortfolioResponse(
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getFoodCategory(),
                profile.getLocation(),
                profile.getPriceMinCents(),
                profile.getPriceMaxCents(),
                accounts.stream()
                        .map(a -> new CreatorPortfolioResponse.AccountView(
                                a.getPlatform(), a.getFollowers(), a.getEngagementRate(), a.getLastPulledAt()))
                        .toList(),
                featuredCampaigns,
                averageRating,
                reviews.size(),
                reviews.stream()
                        .map(r -> new CreatorPortfolioResponse.ReviewView(r.getRating(), r.getText(), r.getCreatedAt()))
                        .toList());
    }
}
