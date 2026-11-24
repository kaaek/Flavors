package com.flavors.mapper;

import com.flavors.dto.OfferResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.Offer;
import com.flavors.enums.OfferState;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Converts an offer into what the brand's offer page shows. */
@Component
public class OfferMapper {

    private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * @param zone the restaurant's time zone: the offer stops at midnight there, so the expiry day is read there too
     */
    public OfferResponse toResponse(Offer offer, Campaign campaign, ZoneId zone, long redemptions, OfferState state,
                                    boolean editable) {
        // expiresAt is the midnight that ends the expiry day, so the day itself is the one before it.
        LocalDate expiresOn = LocalDate.ofInstant(offer.getExpiresAt(), zone).minusDays(1);
        return new OfferResponse(
                offer.getId(),
                offer.getCampaignId(),
                campaign.getCreatorId(),
                offer.getDiscountPercent(),
                expiresOn,
                offer.getExpiresAt(),
                Offer.daysOf(offer.getValidDays()),
                hours(offer.getValidFrom()),
                hours(offer.getValidTo()),
                offer.isAllBranches(),
                offer.getBranchIds().stream().sorted().toList(),
                offer.getMinPurchaseCents(),
                offer.getMaxRedemptions(),
                offer.getPerPersonLimit(),
                redemptions,
                state,
                editable,
                offer.getUpdatedAt());
    }

    /** Hours and minutes lose nothing: the service only stores whole minutes. */
    private static String hours(LocalTime time) {
        return time == null ? null : time.format(HOURS);
    }
}
