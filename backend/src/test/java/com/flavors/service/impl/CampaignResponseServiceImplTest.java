package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.DeliverableItem;
import com.flavors.entity.Campaign;
import com.flavors.entity.CampaignDeliverable;
import com.flavors.entity.CampaignTestFactory;
import com.flavors.entity.CounterOffer;
import com.flavors.entity.Notification;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.CounterOfferStatus;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignResponseStateException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CounterOfferRepository;
import com.flavors.repository.NotificationRepository;
import com.flavors.service.AuditLogger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/** The rules of UC-F4.1 with mocked repositories. The same flow runs on a real database in the IT. */
class CampaignResponseServiceImplTest {

    private static final Long CAMPAIGN_ID = 10L;
    private static final Long BRAND_ID = 20L;
    private static final Long CREATOR_ID = 30L;
    private static final Long ORIGINAL_PRICE = 50000L;

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private CampaignRepository campaignRepository;
    private CounterOfferRepository counterOfferRepository;
    private NotificationRepository notificationRepository;
    private AuditLogger auditLogger;

    private CampaignResponseServiceImpl service;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        counterOfferRepository = mock(CounterOfferRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        auditLogger = mock(AuditLogger.class);

        service = new CampaignResponseServiceImpl(
                campaignRepository,
                counterOfferRepository,
                notificationRepository,
                auditLogger,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A request waiting for the creator, with one Instagram reel and a deadline one day away. */
    private Campaign openRequest() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID, BRAND_ID, CREATOR_ID, "Test campaign", ORIGINAL_PRICE, NOW.plusSeconds(86400));
        campaign.replaceDeliverables(List.of(new CampaignDeliverable(DeliverablePlatform.INSTAGRAM, DeliverableType.REEL, 1)));
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));
        return campaign;
    }

    /** A request the creator countered, with her counter-offer stored. */
    private Campaign counteredRequest(CounterOffer counterOffer) {
        Campaign campaign = CampaignTestFactory.countered(
                CAMPAIGN_ID, BRAND_ID, CREATOR_ID, "Test campaign", ORIGINAL_PRICE);
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));
        when(counterOfferRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.of(counterOffer));
        return campaign;
    }

    private CounterOffer counterOffer(long price) {
        return new CounterOffer(CAMPAIGN_ID, price, List.of(new DeliverableItem("INSTAGRAM", "STORY", 2)));
    }

    private List<Notification> savedNotifications(int expected) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    // TC-F4.1.1
    @Test
    void accept_movesTheCampaignToAwaitingPaymentAndTellsTheBrand() {
        Campaign campaign = openRequest();

        service.acceptCampaign(CREATOR_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.AWAITING_PAYMENT);
        assertThat(campaign.getAgreedPriceCents()).isEqualTo(ORIGINAL_PRICE);
        assertThat(campaign.getPaymentDeadline()).isEqualTo(NOW.plusSeconds(3 * 86400));
        assertThat(campaign.getUpdatedAt()).isEqualTo(NOW);
        verify(campaignRepository).save(campaign);
        verify(auditLogger).record("CAMPAIGN_ACCEPTED", "campaign", CAMPAIGN_ID);

        Notification told = savedNotifications(1).get(0);
        assertThat(told.getUserId()).isEqualTo(BRAND_ID);
        assertThat(told.getType()).isEqualTo(Notification.CAMPAIGN_ACCEPTED);
    }

    // TC-F4.1.2
    @Test
    void reject_closesTheRequestAndTellsTheBrand() {
        Campaign campaign = openRequest();

        service.rejectCampaign(CREATOR_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.REJECTED);
        assertThat(campaign.getAgreedPriceCents()).isNull();
        assertThat(campaign.getPaymentDeadline()).isNull();
        verify(auditLogger).record("CAMPAIGN_REJECTED", "campaign", CAMPAIGN_ID);
        assertThat(savedNotifications(1).get(0).getType()).isEqualTo(Notification.CAMPAIGN_REJECTED);
    }

    @Test
    void counter_storesTheFullTermsAndTellsTheBrand() {
        Campaign campaign = openRequest();

        service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID, new CounterOfferRequest(60000L, null));

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COUNTERED);
        ArgumentCaptor<CounterOffer> stored = ArgumentCaptor.forClass(CounterOffer.class);
        verify(counterOfferRepository).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getPriceCents()).isEqualTo(60000L);
        // What she did not change is copied from the request, so the brand sees one complete offer.
        assertThat(stored.getValue().getDeliverables()).containsExactly(new DeliverableItem("INSTAGRAM", "REEL", 1));
        verify(auditLogger).record("COUNTER_OFFER_SUBMITTED", "campaign", CAMPAIGN_ID);
        Notification told = savedNotifications(1).get(0);
        assertThat(told.getUserId()).isEqualTo(BRAND_ID);
        assertThat(told.getType()).isEqualTo(Notification.COUNTER_OFFER_RECEIVED);
    }

    // TC-F4.1.3
    @Test
    void decline_closesTheRequestAndTellsTheCreator() {
        CounterOffer counterOffer = counterOffer(60000L);
        Campaign campaign = counteredRequest(counterOffer);

        service.declineCounterOffer(BRAND_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.DECLINED);
        assertThat(campaign.getAgreedPriceCents()).isNull();
        assertThat(counterOffer.getStatus()).isEqualTo(CounterOfferStatus.DECLINED);
        verify(auditLogger).record("COUNTER_OFFER_DECLINED", "campaign", CAMPAIGN_ID);

        Notification told = savedNotifications(1).get(0);
        assertThat(told.getUserId()).isEqualTo(CREATOR_ID);
        assertThat(told.getType()).isEqualTo(Notification.COUNTER_OFFER_DECLINED);
    }

    // TC-F4.1.4
    @Test
    void approve_usesTheCounterPriceAndDeliverablesAndAwaitsPayment() {
        CounterOffer counterOffer = counterOffer(65000L);
        Campaign campaign = counteredRequest(counterOffer);

        service.approveCounterOffer(BRAND_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.AWAITING_PAYMENT);
        assertThat(campaign.getAgreedPriceCents()).isEqualTo(65000L);
        assertThat(campaign.getPaymentDeadline()).isEqualTo(NOW.plusSeconds(3 * 86400));
        assertThat(counterOffer.getStatus()).isEqualTo(CounterOfferStatus.APPROVED);
        assertThat(campaign.getDeliverables()).hasSize(1);
        assertThat(campaign.getDeliverables().get(0).getType()).isEqualTo(DeliverableType.STORY);
        assertThat(campaign.getDeliverables().get(0).getCount()).isEqualTo(2);
        verify(auditLogger).record("COUNTER_OFFER_APPROVED", "campaign", CAMPAIGN_ID);
        assertThat(savedNotifications(1).get(0).getUserId()).isEqualTo(CREATOR_ID);
    }

    // TC-F4.1.5
    @Test
    void expiry_endsAnUnansweredRequestAndTellsTheBrand() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID, BRAND_ID, CREATOR_ID, "Test campaign", ORIGINAL_PRICE, NOW.minusSeconds(1));
        when(campaignRepository.findOverdueResponseIds(any(), eq(NOW))).thenReturn(List.of(CAMPAIGN_ID));
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

        int expired = service.expireOverdueCampaignResponses();

        assertThat(expired).isEqualTo(1);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
        verify(auditLogger).record("CAMPAIGN_RESPONSE_EXPIRED", "campaign", CAMPAIGN_ID);
        List<Notification> told = savedNotifications(1);
        assertThat(told.get(0).getUserId()).isEqualTo(BRAND_ID);
        assertThat(told.get(0).getType()).isEqualTo(Notification.CAMPAIGN_RESPONSE_EXPIRED);
    }

    @Test
    void expiry_alsoEndsACounteredRequestAndTellsBothSides() {
        Campaign campaign = CampaignTestFactory.countered(CAMPAIGN_ID, BRAND_ID, CREATOR_ID, "Test campaign", ORIGINAL_PRICE);
        ReflectionTestUtils.setField(campaign, "responseDeadline", NOW.minusSeconds(1));
        when(campaignRepository.findOverdueResponseIds(any(), eq(NOW))).thenReturn(List.of(CAMPAIGN_ID));
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

        int expired = service.expireOverdueCampaignResponses();

        assertThat(expired).isEqualTo(1);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
        assertThat(campaign.getAgreedPriceCents()).isNull();
        List<Notification> told = savedNotifications(2);
        assertThat(told).extracting(Notification::getUserId).containsExactlyInAnyOrder(BRAND_ID, CREATOR_ID);
    }

    @Test
    void expiry_skipsACampaignThatWasAnsweredWhileItWasWaiting() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID, BRAND_ID, CREATOR_ID, "Test campaign", ORIGINAL_PRICE, NOW.minusSeconds(1));
        ReflectionTestUtils.setField(campaign, "status", CampaignStatus.AWAITING_PAYMENT);
        when(campaignRepository.findOverdueResponseIds(any(), eq(NOW))).thenReturn(List.of(CAMPAIGN_ID));
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

        assertThat(service.expireOverdueCampaignResponses()).isZero();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.AWAITING_PAYMENT);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void expiry_goesOnWhenOneCampaignFails() {
        Campaign good = CampaignTestFactory.awaitingCreatorResponse(
                11L, BRAND_ID, CREATOR_ID, "Good", ORIGINAL_PRICE, NOW.minusSeconds(1));
        when(campaignRepository.findOverdueResponseIds(any(), eq(NOW))).thenReturn(List.of(CAMPAIGN_ID, 11L));
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenThrow(new IllegalStateException("boom"));
        when(campaignRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(good));

        assertThat(service.expireOverdueCampaignResponses()).isEqualTo(1);

        assertThat(good.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
    }

    // The whole negotiation shares the 3 days: after the deadline the brand cannot decide any more.
    @Test
    void approveAndDecline_areRefusedAfterTheDeadline() {
        Campaign campaign = counteredRequest(counterOffer(65000L));
        ReflectionTestUtils.setField(campaign, "responseDeadline", NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.approveCounterOffer(BRAND_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignResponseStateException.class);
        assertThatThrownBy(() -> service.declineCounterOffer(BRAND_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COUNTERED);
    }

    // The deadline instant itself is already too late.
    @Test
    void accept_isRefusedExactlyAtTheDeadline() {
        Campaign campaign = openRequest();
        ReflectionTestUtils.setField(campaign, "responseDeadline", NOW);

        assertThatThrownBy(() -> service.acceptCampaign(CREATOR_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void accept_isStillAllowedOneSecondBeforeTheDeadline() {
        Campaign campaign = openRequest();
        ReflectionTestUtils.setField(campaign, "responseDeadline", NOW.plusSeconds(1));

        service.acceptCampaign(CREATOR_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.AWAITING_PAYMENT);
    }

    // TC-F4.1.6: a second counter-offer finds the request already COUNTERED.
    @Test
    void aSecondCounterOffer_isRefused() {
        Campaign campaign = counteredRequest(counterOffer(60000L));

        assertThatThrownBy(() -> service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID, new CounterOfferRequest(61000L, null)))
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COUNTERED);
        verify(counterOfferRepository, never()).saveAndFlush(any());
    }

    // When two counters get past the status check at once, the unique campaign_id decides.
    @Test
    void aCounterOfferTheDatabaseRefusesAsADuplicate_isAConflict() {
        openRequest();
        when(counterOfferRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID, new CounterOfferRequest(60000L, null)))
                .isInstanceOf(CampaignResponseStateException.class);
    }

    @Test
    void aCounterPriceOfZero_isRefused() {
        openRequest();

        assertThatThrownBy(() -> service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID, new CounterOfferRequest(0L, null)))
                .isInstanceOf(ValidationFailedException.class);

        verify(counterOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void aCounterThatChangesNothing_isRefused() {
        openRequest();

        assertThatThrownBy(() -> service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID,
                new CounterOfferRequest(ORIGINAL_PRICE, null)))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID,
                new CounterOfferRequest(null, List.of(new DeliverableItem("Instagram", "Reel", 1)))))
                .isInstanceOf(ValidationFailedException.class);

        verify(counterOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void aCounterWithAnUnknownPlatform_isRefused() {
        openRequest();

        assertThatThrownBy(() -> service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID,
                new CounterOfferRequest(null, List.of(new DeliverableItem("Myspace", "Post", 1)))))
                .isInstanceOf(ValidationFailedException.class);
    }

    // NF-1, and a draft must not show that it exists.
    @Test
    void aDraftNamingTheCreator_isNotFound() {
        Campaign draft = openRequest();
        ReflectionTestUtils.setField(draft, "status", CampaignStatus.DRAFT);

        assertThatThrownBy(() -> service.acceptCampaign(CREATOR_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void anotherCreatorsOrBrandsCampaign_isNotFound() {
        openRequest();

        assertThatThrownBy(() -> service.rejectCampaign(999L, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> service.approveCounterOffer(999L, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void anUnknownCampaign_isNotFound() {
        when(campaignRepository.findByIdForUpdate(CAMPAIGN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptCampaign(CREATOR_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotFoundException.class);
    }
}
