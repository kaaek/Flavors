package com.flavors.dto;

import java.util.List;

/**
 * One brand in the search results. Phone and contact person are deliberately absent: contact
 * details stay private until a campaign relationship exists.
 */
public record BrandSearchResult(
        long brandId,
        String businessName,
        String cuisine,
        String description,
        List<String> branchLocations) {
}
