package com.flavors.dto;

import com.flavors.enums.OfferState;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A campaign's follower offer as the brand sees it. Money is in integer cents, hours are "HH:mm".
 *
 * @param creatorId   the campaign's creator, whom the offer is linked to (FR-F10.1); empty while a draft has none
 * @param expiresOn   the last day the offer works
 * @param expiresAt   the moment it stops working: midnight after {@code expiresOn}, in the restaurant's time zone
 * @param validDays   Monday first
 * @param redemptions how many times it has been used so far
 * @param state       whether it can be used now (FR-F10.4)
 * @param editable    whether the brand can still change it: until the campaign is active
 */
public record OfferResponse(
        long id,
        long campaignId,
        Long creatorId,
        int discountPercent,
        LocalDate expiresOn,
        Instant expiresAt,
        List<DayOfWeek> validDays,
        String validFrom,
        String validTo,
        boolean allBranches,
        List<Long> branchIds,
        long minPurchaseCents,
        Integer maxRedemptions,
        int perPersonLimit,
        long redemptions,
        OfferState state,
        boolean editable,
        Instant updatedAt) {
}
