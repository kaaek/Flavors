package com.flavors.dto;

/**
 * Body of {@code PUT /api/creator/portfolio}: a full replace of her details. Every field is
 * written, so the page sends all five; a missing {@code bio} clears the bio, and a missing
 * category, location or price is refused. It never touches the featured campaigns, which have
 * their own endpoints. An invalid edit saves nothing (EX-F1.2.2).
 */
public record UpdatePortfolioRequest(
        String bio,
        String foodCategory,
        String location,
        Long priceMinCents,
        Long priceMaxCents) {
}