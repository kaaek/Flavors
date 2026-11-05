package com.flavors.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** The query string of {@code GET /api/creator/brands/search}. Every field is optional. */
public record BrandSearchCriteria(
        @Size(max = 200, message = "Search text is too long.") String q,
        @Size(max = 50, message = "Cuisine is too long.") String cuisine,
        @Size(max = 200, message = "Location is too long.") String location,
        @Min(value = 0, message = "Page can't be negative.") Integer page,
        @Min(value = 1, message = "Size must be at least 1.")
        @Max(value = 50, message = "Size can't be above 50.") Integer size) {

    public BrandSearchCriteria {
        page = page == null ? 0 : page;
        size = size == null ? CreatorSearchCriteria.DEFAULT_SIZE : size;
    }
}
