package com.flavors.service.impl;

import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.DeliverableItem;
import com.flavors.entity.Campaign;
import com.flavors.entity.CampaignDeliverable;
import com.flavors.entity.CounterOffer;
import com.flavors.entity.Notification;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CounterOfferRepository;
import com.flavors.repository.NotificationRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.CampaignResponseService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The creator's answer to a campaign request and the brand's decision on her counter-offer (UC-F4.1). */
@Service
@Transactional
public class CampaignResponseServiceImpl implements CampaignResponseService {

    private final CampaignRepository campaignRepository;
    private final CounterOfferRepository counterOfferRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    @Autowired
    public CampaignResponseServiceImpl(
            CampaignRepository campaignRepository,
            CounterOfferRepository counterOfferRepository,
            NotificationRepository notificationRepository,
            AuditLogger auditLogger) {

        this(
                campaignRepository,
                counterOfferRepository,
                notificationRepository,
                auditLogger,
                Clock.systemUTC());
    }

    CampaignResponseServiceImpl(
            CampaignRepository campaignRepository,
            CounterOfferRepository counterOfferRepository,
            NotificationRepository notificationRepository,
            AuditLogger auditLogger,
            Clock clock) {

        this.campaignRepository = campaignRepository;
        this.counterOfferRepository = counterOfferRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public void acceptCampaign(Long creatorId, Long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        requireCreatorOwnership(campaign, creatorId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);

        campaign.accept(now);
        campaignRepository.save(campaign);
        auditLogger.record("CAMPAIGN_ACCEPTED", "campaign", campaignId);
    }

    @Override
    public void rejectCampaign(Long creatorId, Long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        requireCreatorOwnership(campaign, creatorId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);

        campaign.reject(now);
        campaignRepository.save(campaign);
        auditLogger.record("CAMPAIGN_REJECTED", "campaign", campaignId);
    }

    @Override
    public void submitCounterOffer(
            Long creatorId,
            Long campaignId,
            CounterOfferRequest request) {

        Campaign campaign = findCampaign(campaignId);
        requireCreatorOwnership(campaign, creatorId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);

        if (request == null
                || (request.priceCents() == null
                        && request.deliverables() == null)
                || (request.priceCents() != null
                        && request.priceCents() < 0)) {

            throw new ValidationFailedException(
                    "counterOffer",
                    "You must change the price, the deliverables, or both.");
        }

        if (request.deliverables() != null
                && request.deliverables().isEmpty()) {

            throw new ValidationFailedException(
                    "counterOffer",
                    "At least one deliverable is required.");
        }

        if (counterOfferRepository.existsByCampaignId(campaignId)) {
            throw new ValidationFailedException(
                    "counterOffer",
                    "A campaign can only have one counter-offer.");
        }

        Long proposedPrice = request.priceCents() != null
                ? request.priceCents()
                : campaign.getPriceOfferedCents();

        List<DeliverableItem> proposedDeliverables;

        if (request.deliverables() != null) {
            proposedDeliverables = normalise(request.deliverables());
        } else {
            proposedDeliverables = campaign.getDeliverables().stream()
                    .map(item -> new DeliverableItem(
                            item.getPlatform().name(),
                            item.getType().name(),
                            item.getCount()))
                    .toList();
        }

        if (proposedDeliverables.isEmpty()) {
            throw new ValidationFailedException(
                    "counterOffer",
                    "The campaign must have at least one deliverable.");
        }

        campaign.submitCounterOffer(now);

        CounterOffer counterOffer = new CounterOffer(
                campaignId,
                proposedPrice,
                proposedDeliverables);

        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);
        auditLogger.record("COUNTER_OFFER_SUBMITTED", "campaign", campaignId);
    }

    @Override
    public void approveCounterOffer(Long brandId, Long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        requireBrandOwnership(campaign, brandId);
        requireStatus(campaign, CampaignStatus.COUNTERED);

        CounterOffer counterOffer = findCounterOffer(campaignId);
        Instant now = clock.instant();

        counterOffer.approve(now);
        campaign.approveCounterOffer(counterOffer.getPriceCents(), now);

        List<CampaignDeliverable> newDeliverables =
                counterOffer.getDeliverables().stream()
                        .map(item -> new CampaignDeliverable(
                                parseEnum(DeliverablePlatform.class, item.platform()),
                                parseEnum(DeliverableType.class, item.type()),
                                item.count()))
                        .toList();

        campaign.replaceDeliverables(newDeliverables);

        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);
        auditLogger.record("COUNTER_OFFER_APPROVED", "campaign", campaignId);
    }

    @Override
    public void declineCounterOffer(Long brandId, Long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        requireBrandOwnership(campaign, brandId);
        requireStatus(campaign, CampaignStatus.COUNTERED);

        CounterOffer counterOffer = findCounterOffer(campaignId);
        Instant now = clock.instant();

        counterOffer.decline(now);
        campaign.declineCounterOffer(now);

        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);
        auditLogger.record("COUNTER_OFFER_DECLINED", "campaign", campaignId);

        notificationRepository.save(new Notification(
                campaign.getCreatorId(),
                campaignId,
                "COUNTER_OFFER_DECLINED",
                "Your counter-offer for campaign "
                        + campaign.getName() + " was declined."));
    }

    @Override
    public int expireOverdueCampaignResponses() {
        Instant now = clock.instant();

        List<Campaign> overdueCampaigns =
                campaignRepository.findByStatusAndResponseDeadlineLessThanEqual(
                        CampaignStatus.AWAITING_CREATOR_RESPONSE,
                        now);

        for (Campaign campaign : overdueCampaigns) {
            campaign.expireResponse(now);
            campaignRepository.save(campaign);
            auditLogger.record("CAMPAIGN_RESPONSE_EXPIRED", "campaign", campaign.getId());

            notificationRepository.save(new Notification(
                    campaign.getBrandId(),
                    campaign.getId(),
                    "CAMPAIGN_RESPONSE_EXPIRED",
                    "The creator did not respond to campaign "
                            + campaign.getName()
                            + " before the response deadline."));
        }

        return overdueCampaigns.size();
    }

    /** Checks each proposed deliverable and stores platform and type the way the campaign does (upper case). */
    private List<DeliverableItem> normalise(List<DeliverableItem> items) {
        List<DeliverableItem> result = new ArrayList<>();
        for (DeliverableItem item : items) {
            DeliverablePlatform platform = parseEnum(DeliverablePlatform.class, item.platform());
            DeliverableType type = parseEnum(DeliverableType.class, item.type());
            if (platform == null || type == null) {
                throw new ValidationFailedException(
                        "counterOffer",
                        "Each deliverable needs a known platform and type.");
            }
            if (item.count() == null || item.count() < 1) {
                throw new ValidationFailedException(
                        "counterOffer",
                        "Each deliverable needs a count of 1 or more.");
            }
            result.add(new DeliverableItem(platform.name(), type.name(), item.count()));
        }
        return List.copyOf(result);
    }

    /** The enum value for a text such as "Instagram" or "reel", or null when there is none. */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Campaign findCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Campaign not found."));
    }

    private CounterOffer findCounterOffer(Long campaignId) {
        return counterOfferRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new ValidationFailedException(
                        "counterOffer",
                        "No counter-offer exists for this campaign."));
    }

    private void requireCreatorOwnership(Campaign campaign, Long creatorId) {
        if (!Objects.equals(campaign.getCreatorId(), creatorId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Campaign not found.");
        }
    }

    private void requireBrandOwnership(Campaign campaign, Long brandId) {
        if (!Objects.equals(campaign.getBrandId(), brandId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Campaign not found.");
        }
    }

    private void requireStatus(Campaign campaign, CampaignStatus expected) {
        if (campaign.getStatus() != expected) {
            throw new ValidationFailedException(
                    "status",
                    "Campaign is not in the required status.");
        }
    }

    private void requireResponseWindowOpen(Campaign campaign, Instant now) {
        if (campaign.getResponseDeadline() == null
                || !campaign.getResponseDeadline().isAfter(now)) {

            throw new ValidationFailedException(
                    "responseDeadline",
                    "The creator response deadline has passed.");
        }
    }
}
