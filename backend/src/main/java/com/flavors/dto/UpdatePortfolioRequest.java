package com.flavors.dto;
 
import java.util.List;
 
/**
 * Body of {@code PUT /api/creator/portfolio}: her edited details and the campaigns she wants
 * featured. It is saved as one step, so an invalid edit saves nothing (EX-F1.2.2).
 *
 * <p>{@code featuredCampaignIds} is the complete list she wants featured; a missing list means none.
 */
public record UpdatePortfolioRequest(
        String bio,
        String foodCategory,
        String location,
        Long priceMinCents,
        Long priceMaxCents,
        List<Long> featuredCampaignIds) {
}