package com.flavors.dto;

import java.math.BigDecimal;

/**
 * One creator in the search results. Platform, followers and engagement are null for creators who
 * haven't connected a social account; roi and redemptions are null until campaigns exist.
 */
public record CreatorSearchResult(
        long creatorId,
        String displayName,
        String foodCategory,
        String location,
        String platform,
        Long followers,
        BigDecimal engagementRate,
        long priceMinCents,
        long priceMaxCents,
        BigDecimal roi,
        Long redemptions,
        String storefrontSlug,
        boolean saved) {
}
