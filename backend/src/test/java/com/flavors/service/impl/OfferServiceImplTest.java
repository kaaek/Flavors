package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.entity.Offer;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.OfferState;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Whether an offer is live (FR-F10.4) for every campaign status, and the valid-day bits of V4. */
class OfferServiceImplTest {

    private static final Instant NOW = Instant.parse("2030-01-10T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void everyCampaignStatusHasExactlyOneMeaningForTheOffer() {
        EnumSet<CampaignStatus> all = EnumSet.noneOf(CampaignStatus.class);
        all.addAll(OfferServiceImpl.EDITABLE);
        all.addAll(OfferServiceImpl.RUNNING);
        all.addAll(OfferServiceImpl.OVER);
        all.add(CampaignStatus.DISPUTED);
        // A new campaign status must be placed in one of the groups, or this test fails.
        assertThat(all).containsExactlyInAnyOrder(CampaignStatus.values());
        assertThat(OfferServiceImpl.EDITABLE).doesNotContainAnyElementsOf(OfferServiceImpl.RUNNING)
                .doesNotContainAnyElementsOf(OfferServiceImpl.OVER).doesNotContain(CampaignStatus.DISPUTED);
        assertThat(OfferServiceImpl.RUNNING).doesNotContainAnyElementsOf(OfferServiceImpl.OVER);
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class, names = {"ACTIVE", "PROOF_SUBMITTED", "CORRECTIONS_REQUESTED"})
    void liveWhileTheCampaignRuns(CampaignStatus status) {
        assertThat(OfferServiceImpl.stateOf(status, LATER, null, 0, NOW)).isEqualTo(OfferState.LIVE);
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class,
            names = {"DRAFT", "AWAITING_CREATOR_RESPONSE", "COUNTERED", "AWAITING_PAYMENT"})
    void notLiveBeforeTheCampaignIsActive(CampaignStatus status) {
        assertThat(OfferServiceImpl.stateOf(status, LATER, null, 0, NOW)).isEqualTo(OfferState.NOT_LIVE_YET);
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class,
            names = {"COMPLETED", "CANCELLED", "REMOVED", "REJECTED", "DECLINED", "EXPIRED"})
    void endedWhenTheCampaignIsOverEvenIfAlsoExpiredOrFull(CampaignStatus status) {
        assertThat(OfferServiceImpl.stateOf(status, LATER, null, 0, NOW)).isEqualTo(OfferState.ENDED);
        assertThat(OfferServiceImpl.stateOf(status, NOW.minusSeconds(1), 1, 5, NOW)).isEqualTo(OfferState.ENDED);
    }

    @Test
    void pausedDuringADispute() {
        assertThat(OfferServiceImpl.stateOf(CampaignStatus.DISPUTED, LATER, null, 0, NOW)).isEqualTo(OfferState.PAUSED);
    }

    @Test
    void expiredFromTheExpiryMomentOn() {
        assertThat(OfferServiceImpl.stateOf(CampaignStatus.ACTIVE, NOW, null, 0, NOW)).isEqualTo(OfferState.EXPIRED);
        assertThat(OfferServiceImpl.stateOf(CampaignStatus.ACTIVE, NOW.plusMillis(1), null, 0, NOW))
                .isEqualTo(OfferState.LIVE);
    }

    @Test
    void fullOnceTheMaximumIsReachedAndNeverWithoutAMaximum() {
        assertThat(OfferServiceImpl.stateOf(CampaignStatus.ACTIVE, LATER, 3, 2, NOW)).isEqualTo(OfferState.LIVE);
        assertThat(OfferServiceImpl.stateOf(CampaignStatus.ACTIVE, LATER, 3, 3, NOW)).isEqualTo(OfferState.FULL);
        assertThat(OfferServiceImpl.stateOf(CampaignStatus.ACTIVE, LATER, null, 1_000_000, NOW))
                .isEqualTo(OfferState.LIVE);
    }

    @Test
    void validDaysUseOneBitPerDayMondayFirst() {
        assertThat(Offer.bitsOf(List.of(DayOfWeek.MONDAY))).isEqualTo((short) 1);
        assertThat(Offer.bitsOf(List.of(DayOfWeek.SUNDAY))).isEqualTo((short) 64);
        assertThat(Offer.bitsOf(List.of(DayOfWeek.values()))).isEqualTo(Offer.EVERY_DAY);
        // Order and repeats don't matter.
        assertThat(Offer.bitsOf(List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY))).isEqualTo((short) 17);

        assertThat(Offer.daysOf((short) 17)).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(Offer.daysOf(Offer.EVERY_DAY)).containsExactly(DayOfWeek.values());
    }
}
