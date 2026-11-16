package com.flavors.service.impl;

import static java.util.stream.Collectors.toMap;

import com.flavors.dto.CreatorPortfolioResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.PortfolioFeature;
import com.flavors.entity.Review;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.mapper.CreatorPortfolioMapper;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.PortfolioFeatureRepository;
import com.flavors.repository.RedemptionRepository;
import com.flavors.repository.ReviewRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.PortfolioViewService;
import com.flavors.util.Roi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PortfolioViewServiceImpl implements PortfolioViewService {

    private final CreatorProfileRepository profiles;
    private final SocialAccountRepository socialAccounts;
    private final CampaignRepository campaigns;
    private final PortfolioFeatureRepository features;
    private final RedemptionRepository redemptions;
    private final ReviewRepository reviews;
    private final CreatorPortfolioMapper mapper;

    PortfolioViewServiceImpl(CreatorProfileRepository profiles, SocialAccountRepository socialAccounts,
                             CampaignRepository campaigns, PortfolioFeatureRepository features,
                             RedemptionRepository redemptions, ReviewRepository reviews,
                             CreatorPortfolioMapper mapper) {
        this.profiles = profiles;
        this.socialAccounts = socialAccounts;
        this.campaigns = campaigns;
        this.features = features;
        this.redemptions = redemptions;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    @Override
    public CreatorPortfolioResponse view(long creatorId) {
        // A creator who waits for approval has no portfolio, and a brand cannot tell her from one who does not exist.
        CreatorProfile profile = profiles.findById(creatorId)
                .filter(p -> p.getVerificationStatus() == VerificationStatus.APPROVED)
                .orElseThrow(() -> new CreatorNotFoundException(creatorId));

        List<Review> creatorReviews = reviews.findAllForCreator(creatorId);
        return mapper.toResponse(
                profile,
                socialAccounts.findByCreatorId(creatorId),
                featuredCampaigns(creatorId),
                creatorReviews,
                averageRating(creatorReviews));
    }

    /** Her featured campaigns, newest first, each with its redemptions and ROI. */
    private List<CreatorPortfolioResponse.FeaturedCampaignView> featuredCampaigns(long creatorId) {
        List<Long> featuredIds = features.findByCreatorId(creatorId).stream()
                .map(PortfolioFeature::getCampaignId)
                .toList();
        if (featuredIds.isEmpty()) {
            return List.of();
        }

        List<Campaign> completed = campaigns.findAllById(featuredIds).stream()
                .filter(c -> c.getStatus() == CampaignStatus.COMPLETED)
                .sorted(Comparator.comparing(Campaign::getId).reversed())
                .toList();
        if (completed.isEmpty()) {
            return List.of();
        }

        Map<Long, RedemptionRepository.CampaignTotals> totals = redemptions
                .totalsByCampaign(completed.stream().map(Campaign::getId).toList()).stream()
                .collect(toMap(RedemptionRepository.CampaignTotals::getCampaignId, t -> t));

        return completed.stream().map(campaign -> {
            RedemptionRepository.CampaignTotals t = totals.get(campaign.getId());
            long count = t == null ? 0 : t.getRedemptions();
            // FR-F1.3 / EX-F1.1.1: verified only if completed AND at least one tracked redemption.
            boolean verified = campaign.getStatus() == CampaignStatus.COMPLETED && count > 0;
            BigDecimal roi = null;
            if (verified) {
                long agreedPrice = campaign.getAgreedPriceCents() == null ? 0 : campaign.getAgreedPriceCents();
                // Cost = the agreed price plus the discounts given; revenue = the bills before the discount.
                roi = Roi.percent(t.getRevenueCents(), agreedPrice + t.getDiscountCents());
            }
            return mapper.toFeatured(campaign, count, verified, roi);
        }).toList();
    }

    private static BigDecimal averageRating(List<Review> creatorReviews) {
        if (creatorReviews.isEmpty()) {
            return null;
        }
        double average = creatorReviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }
}
