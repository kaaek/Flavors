package com.flavors.service.impl;

import com.flavors.entity.Campaign;
import com.flavors.entity.Review;
import com.flavors.enums.CampaignStatus;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.ReviewNotAllowedException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.ReviewRepository;
import com.flavors.service.CampaignReviewService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignReviewServiceImpl implements CampaignReviewService {

    private final CampaignRepository campaigns;
    private final ReviewRepository reviews;

    public CampaignReviewServiceImpl(
            CampaignRepository campaigns,
            ReviewRepository reviews) {
        this.campaigns = campaigns;
        this.reviews = reviews;
    }

    @Override
    @Transactional(readOnly = true)
    public Review get(long brandId, long campaignId) {
        campaigns.findByIdAndBrandId(campaignId, brandId)
                .orElseThrow(CampaignNotFoundException::new);

        return reviews.findByCampaignId(campaignId).orElse(null);
    }

    @Override
    @Transactional
    public Review submit(long brandId, long campaignId, short rating, String text) {

        // Lock the campaign and verify ownership.
        Campaign campaign = campaigns.findByIdAndBrandIdForUpdate(campaignId, brandId)
                .orElseThrow(CampaignNotFoundException::new);

        // Reviews are allowed only after completion.
        if (campaign.getStatus() != CampaignStatus.COMPLETED) {
            throw new ReviewNotAllowedException("Campaign is not completed");
        }

        // Only one review per campaign.
        if (reviews.existsByCampaignId(campaignId)) {
            throw new ReviewNotAllowedException("Campaign already reviewed");
        }

        // Validate rating.
        if (rating < 1 || rating > 5) {
            throw new ValidationFailedException(
                    "rating", "Rating must be between 1 and 5");
        }

        // Normalize optional review text.
        String normalizedText = text == null || text.isBlank()
                ? null
                : text.trim();

        if (normalizedText != null && normalizedText.length() > 1000) {
            throw new ValidationFailedException(
                    "text", "Review cannot exceed 1000 characters");
        }

        // Save the review.
        Review review = new Review(
                campaignId, rating, normalizedText, Instant.now());

        return reviews.saveAndFlush(review);
    }
}
