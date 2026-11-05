package com.flavors.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The query string of {@code GET /api/brand/creators/search} (FR-F2.1-F2.5). Every field is
 * optional; the filters combine with AND. {@code verified} is accepted but changes nothing, since
 * only approved creators are ever searchable (TC-F2.1.4).
 */
public record CreatorSearchCriteria(
        @Size(max = 200, message = "Search text is too long.") String q,
        @Size(max = 100, message = "Location is too long.") String location,
        @Size(max = 50, message = "Category is too long.") String category,
        @Size(max = 30, message = "Platform is too long.") String platform,
        @PositiveOrZero(message = "Minimum followers can't be negative.") Long minFollowers,
        @PositiveOrZero(message = "Maximum followers can't be negative.") Long maxFollowers,
        @PositiveOrZero(message = "Minimum engagement can't be negative.") BigDecimal minEngagement,
        @PositiveOrZero(message = "Minimum price can't be negative.") Long minPriceCents,
        @PositiveOrZero(message = "Maximum price can't be negative.") Long maxPriceCents,
        @PositiveOrZero(message = "Minimum ROI can't be negative.") BigDecimal minRoi,
        @PositiveOrZero(message = "Minimum redemptions can't be negative.") Long minRedemptions,
        Boolean verified,
        @Min(value = 0, message = "Page can't be negative.") Integer page,
        @Min(value = 1, message = "Size must be at least 1.")
        @Max(value = 50, message = "Size can't be above 50.") Integer size) {

    public static final int DEFAULT_SIZE = 20;

    public CreatorSearchCriteria {
        page = page == null ? 0 : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    @AssertTrue(message = "Minimum followers can't be above the maximum.")
    public boolean isFollowerRangeValid() {
        return minFollowers == null || maxFollowers == null || minFollowers <= maxFollowers;
    }

    @AssertTrue(message = "Minimum price can't be above the maximum price.")
    public boolean isPriceRangeValid() {
        return minPriceCents == null || maxPriceCents == null || minPriceCents <= maxPriceCents;
    }
}
