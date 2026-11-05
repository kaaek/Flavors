package com.flavors.repository;

import java.util.List;

/** One row of the brand search query, with its branch locations. */
public record BrandSearchRow(
        long brandId,
        String businessName,
        String cuisine,
        String description,
        List<String> branchLocations) {
}
