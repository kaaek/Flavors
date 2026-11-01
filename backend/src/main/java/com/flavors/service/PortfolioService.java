package com.flavors.service;

import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import java.util.Map;

/** The creator's Manage Portfolio page (UC-F1.2). Every method needs an approved profile. */
public interface PortfolioService {

    /** Her profile, connected accounts and completed campaigns. */
    PortfolioResponse getMyPortfolio(long creatorId);

    /**
     * Full replace of her details (bio, category, location, price range). It never touches the featured
     * campaigns. An invalid edit saves nothing and throws {@link com.flavors.exception.InvalidPortfolioException}.
     */
    PortfolioResponse updateMyPortfolio(long creatorId, UpdatePortfolioRequest request);

    /**
     * Partial edit: only the fields in {@code changes} change, and the merged result is validated.
     * A key sent with a null value sets that field to null (only the bio may be empty). Unknown keys are refused.
     */
    PortfolioResponse patchMyPortfolio(long creatorId, Map<String, Object> changes);

    /**
     * Features one of her COMPLETED campaigns (FR-F1.6).
     *
     * @throws com.flavors.exception.InvalidPortfolioException if it is not completed or not hers (EX-F1.2.1)
     * @throws com.flavors.exception.AlreadyFeaturedException if it is already featured
     */
    PortfolioResponse.CampaignView featureCampaign(long creatorId, Long campaignId);

    /**
     * Stops featuring one campaign.
     *
     * @throws com.flavors.exception.FeatureNotFoundException if she does not feature it
     */
    void unfeatureCampaign(long creatorId, long campaignId);
}