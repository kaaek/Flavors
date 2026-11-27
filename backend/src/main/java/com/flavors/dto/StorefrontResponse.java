package com.flavors.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** Public-only fields; never exposes accounts, diner data, internal campaign financials or redemption identities. */
public record StorefrontResponse(String displayName, String bio, String foodCategory, String location,
                                 String slug, List<LiveOffer> offers) {
    public record Location(String name, String location) {}

    public record LiveOffer(long id, String restaurant, int discountPercent, LocalDate expiresOn,
                            List<DayOfWeek> validDays, String validFrom, String validTo,
                            long minPurchaseCents, int perPersonLimit, List<Location> branches) {}
}
