
package com.flavors.service.impl;
import com.flavors.service.AuditLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flavors.entity.Campaign;
import com.flavors.entity.CampaignTestFactory;
import com.flavors.enums.CampaignStatus;

import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CounterOfferRepository;
import com.flavors.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flavors.dto.DeliverableItem;
import com.flavors.entity.CounterOffer;
import com.flavors.entity.Notification;
import com.flavors.enums.CounterOfferStatus;
import org.mockito.ArgumentCaptor;
import com.flavors.dto.CounterOfferRequest;
import com.flavors.exception.ValidationFailedException;

class CampaignResponseServiceImplTest {

    private static final Long CAMPAIGN_ID = 10L;
    private static final Long BRAND_ID = 20L;
    private static final Long CREATOR_ID = 30L;
    private static final Long ORIGINAL_PRICE = 50000L;

    private static final Instant NOW =
            Instant.parse("2026-11-07T12:00:00Z");

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
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new CampaignResponseServiceImpl(
                campaignRepository,
                counterOfferRepository,
                notificationRepository,
                auditLogger,
                clock);
    }

    @Test
    void acceptCampaign_shouldMoveCampaignToAwaitingPayment() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID,
                BRAND_ID,
                CREATOR_ID,
                "Test campaign",
                ORIGINAL_PRICE,
                NOW.plusSeconds(86400));

        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        service.acceptCampaign(CREATOR_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus())
                .isEqualTo(CampaignStatus.AWAITING_PAYMENT);

        assertThat(campaign.getAgreedPriceCents())
                .isEqualTo(ORIGINAL_PRICE);

        assertThat(campaign.getPaymentDeadline())
                .isEqualTo(NOW.plusSeconds(3 * 86400));

        verify(campaignRepository).save(campaign);
        verify(auditLogger).record("CAMPAIGN_ACCEPTED", "campaign", CAMPAIGN_ID);
    }

    @Test
    void rejectCampaign_shouldMoveCampaignToRejected() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID,
                BRAND_ID,
                CREATOR_ID,
                "Test campaign",
                ORIGINAL_PRICE,
                NOW.plusSeconds(86400));

        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        service.rejectCampaign(CREATOR_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus())
                .isEqualTo(CampaignStatus.REJECTED);

        assertThat(campaign.getAgreedPriceCents()).isNull();
        assertThat(campaign.getPaymentDeadline()).isNull();

        verify(campaignRepository).save(campaign);
    }

    @Test
    void declineCounterOffer_shouldDeclineAndNotifyCreator() {
        Campaign campaign = CampaignTestFactory.countered(
                CAMPAIGN_ID,
                BRAND_ID,
                CREATOR_ID,
                "Test campaign",
                ORIGINAL_PRICE);

        CounterOffer counterOffer = new CounterOffer(
        CAMPAIGN_ID,
        60000L,
        java.util.List.of(
                new DeliverableItem("Instagram", "Story", 2)));
        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        when(counterOfferRepository.findByCampaignId(CAMPAIGN_ID))
                .thenReturn(Optional.of(counterOffer));

        service.declineCounterOffer(BRAND_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus())
                .isEqualTo(CampaignStatus.DECLINED);

        assertThat(counterOffer.getStatus())
        .isEqualTo(CounterOfferStatus.DECLINED);

        ArgumentCaptor<Notification> notificationCaptor =
        ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(notificationCaptor.capture());

        assertThat(notificationCaptor.getValue().getUserId())
                .isEqualTo(CREATOR_ID);

        assertThat(notificationCaptor.getValue().getCampaignId())
                .isEqualTo(CAMPAIGN_ID);

        assertThat(notificationCaptor.getValue().getType())
                .isEqualTo("COUNTER_OFFER_DECLINED");
    }
        @Test
    void approveCounterOffer_shouldUseCounterPriceAndAwaitPayment() {
        Campaign campaign = CampaignTestFactory.countered(
                CAMPAIGN_ID,
                BRAND_ID,
                CREATOR_ID,
                "Test campaign",
                ORIGINAL_PRICE);

        CounterOffer counterOffer = new CounterOffer(
                CAMPAIGN_ID,
                65000L,
                java.util.List.of(
                        new DeliverableItem("Instagram", "Story", 2)));

        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        when(counterOfferRepository.findByCampaignId(CAMPAIGN_ID))
                .thenReturn(Optional.of(counterOffer));

        service.approveCounterOffer(BRAND_ID, CAMPAIGN_ID);

        assertThat(campaign.getStatus())
                .isEqualTo(CampaignStatus.AWAITING_PAYMENT);

        assertThat(campaign.getAgreedPriceCents())
                .isEqualTo(65000L);

        assertThat(campaign.getPaymentDeadline())
                .isEqualTo(NOW.plusSeconds(3 * 86400));

        assertThat(counterOffer.getStatus())
                .isEqualTo(CounterOfferStatus.APPROVED);

        assertThat(campaign.getDeliverables()).hasSize(1);
        verify(campaignRepository).save(campaign);
        verify(counterOfferRepository).save(counterOffer);
    }
        @Test
    void expireOverdueCampaignResponses_shouldExpireAndNotifyBrand() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID,
                BRAND_ID,
                CREATOR_ID,
                "Test campaign",
                ORIGINAL_PRICE,
                NOW.minusSeconds(1));

        when(campaignRepository
                .findByStatusAndResponseDeadlineLessThanEqual(
                        CampaignStatus.AWAITING_CREATOR_RESPONSE,
                        NOW))
                .thenReturn(List.of(campaign));

        int expiredCount = service.expireOverdueCampaignResponses();

        assertThat(expiredCount).isEqualTo(1);

        assertThat(campaign.getStatus())
                .isEqualTo(CampaignStatus.EXPIRED);

        verify(campaignRepository).save(campaign);

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(notificationCaptor.capture());

        assertThat(notificationCaptor.getValue().getUserId())
                .isEqualTo(BRAND_ID);

        assertThat(notificationCaptor.getValue().getCampaignId())
                .isEqualTo(CAMPAIGN_ID);

        assertThat(notificationCaptor.getValue().getType())
                .isEqualTo("CAMPAIGN_RESPONSE_EXPIRED");
    }
    @Test
    void submitCounterOffer_shouldRejectSecondCounterOffer() {
        Campaign campaign = CampaignTestFactory.awaitingCreatorResponse(
                CAMPAIGN_ID,
                BRAND_ID,
                CREATOR_ID,
                "Test campaign",
                ORIGINAL_PRICE,
                NOW.plusSeconds(86400));

        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        when(counterOfferRepository.existsByCampaignId(CAMPAIGN_ID))
                .thenReturn(true);

        CounterOfferRequest request = new CounterOfferRequest(
                60000L,
                null);

        assertThatThrownBy(() ->
                service.submitCounterOffer(CREATOR_ID, CAMPAIGN_ID, request))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("only have one counter-offer");

        verify(counterOfferRepository).existsByCampaignId(CAMPAIGN_ID);
        verify(counterOfferRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(CounterOffer.class));
    }
}
