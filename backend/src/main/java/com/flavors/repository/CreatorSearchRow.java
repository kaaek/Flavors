package com.flavors.repository;

import java.math.BigDecimal;

/** One row of the creator search query, before it is turned into the API object. */
public record CreatorSearchRow(
        long creatorId,
        String displayName,
        String foodCategory,
        String location,
        String platform,
        Long followers,
        BigDecimal engagementRate,
        long priceMinCents,
        long priceMaxCents,
        String storefrontSlug,
        boolean saved) {
}
