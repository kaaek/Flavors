package com.flavors.dto;

import jakarta.validation.constraints.Size;

/** What a brand can change about how creators see it. Blank clears the field. */
public record UpdateBrandProfileRequest(
        @Size(max = 50, message = "Cuisine must be at most 50 characters.")
        String cuisine,
        @Size(max = 1000, message = "Description must be at most 1000 characters.")
        String description) {
}
