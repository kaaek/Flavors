package com.flavors.service.impl;

import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.DeliverableItem;
import com.flavors.entity.Campaign;
import com.flavors.entity.CampaignDeliverable;
import com.flavors.entity.CounterOffer;
import com.flavors.entity.Notification;
import com.flavors.enums.CampaignStatus;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.CampaignDeliverableRepository;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CounterOfferRepository;
import com.flavors.repository.NotificationRepository;
import com.flavors.service.CampaignResponseService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.SocialAccountDisconnectedException;
import com.flavors.repository.SocialAccountRepository;
@Service
@Transactional
public class CampaignResponseServiceImpl implements CampaignResponseService {

        private final CampaignRepository campaignRepository;
    private final CounterOfferRepository counterOfferRepository;
    private final CampaignDeliverableRepository deliverableRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final SocialAccountRepository socialAccountRepository;
        
            @Autowired
    public CampaignResponseServiceImpl(
            CampaignRepository campaignRepository,
            CounterOfferRepository counterOfferRepository,
            CampaignDeliverableRepository deliverableRepository,
            NotificationRepository notificationRepository,
            SocialAccountRepository socialAccountRepository) {

        this(
                campaignRepository,
                counterOfferRepository,
                deliverableRepository,
                notificationRepository,
                socialAccountRepository,
                Clock.systemUTC());
    }

    CampaignResponseServiceImpl(
            CampaignRepository campaignRepository,
            CounterOfferRepository counterOfferRepository,
            CampaignDeliverableRepository deliverableRepository,
            NotificationRepository notificationRepository,
            SocialAccountRepository socialAccountRepository,
            Clock clock) {

        this.campaignRepository = campaignRepository;
        this.counterOfferRepository = counterOfferRepository;
        this.deliverableRepository = deliverableRepository;
        this.notificationRepository = notificationRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.clock = clock;
    }

    @Override
    public void acceptCampaign(Long creatorId, Long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        requireCreatorOwnership(campaign, creatorId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);
        requireConnectedAccount(creatorId);
        
        campaign.accept(now);
        campaignRepository.save(campaign);
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
        proposedDeliverables = List.copyOf(request.deliverables());
    } else {
        proposedDeliverables = deliverableRepository
                .findByCampaignId(campaignId)
                .stream()
                .map(item -> new DeliverableItem(
                        item.getPlatform(),
                        item.getType(),
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

        deliverableRepository.deleteByCampaignId(campaignId);

        List<CampaignDeliverable> newDeliverables =
                counterOffer.getDeliverables().stream()
                        .map(item -> new CampaignDeliverable(
                                campaignId,
                                item.platform(),
                                item.type(),
                                item.count()))
                        .toList();

        deliverableRepository.saveAll(newDeliverables);
        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);
    }

    @Override
    public void declineCounterOffer(Long brandId, Long campaignId) {
        Campaign campaign = findCampaign(campaignId);
        requireBrandOwnership(campaign, brandId);
        requireStatus(campaign, CampaignStatus.COUNTERED);

        CounterOffer counterOffer = findCounterOffer(campaignId);
        Instant now = clock.instant();

        counterOffer.decline(now);
        campaign.declineCounterOffer();

        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);

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

    private void requireCreatorOwnership(
            Campaign campaign, Long creatorId) {

        if (!java.util.Objects.equals(campaign.getCreatorId(), creatorId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Campaign not found.");
        }
    }

    private void requireBrandOwnership(
            Campaign campaign, Long brandId) {

        if (!java.util.Objects.equals(campaign.getBrandId(), brandId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Campaign not found.");
        }
    }

    private void requireStatus(
            Campaign campaign, CampaignStatus expected) {

        if (campaign.getStatus() != expected) {
            throw new ValidationFailedException(
                    "status",
                    "Campaign is not in the required status.");
        }
    }

    private void requireResponseWindowOpen(
            Campaign campaign, Instant now) {

        if (campaign.getResponseDeadline() == null
                || !campaign.getResponseDeadline().isAfter(now)) {

            throw new ValidationFailedException(
                    "responseDeadline",
                    "The creator response deadline has passed.");
        }
 }
    /** FT-68: with no connected social account she can't accept a new campaign until she reconnects. */
    private void requireConnectedAccount(Long creatorId) {
        if (!socialAccountRepository.existsByCreatorIdAndStatus(
                creatorId, SocialAccountStatus.CONNECTED)) {
            throw new SocialAccountDisconnectedException();
        }
    }
}  