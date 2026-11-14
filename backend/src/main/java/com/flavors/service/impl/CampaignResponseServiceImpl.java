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
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignResponseStateException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The creator's answer to a campaign request and the brand's decision on her counter-offer (UC-F4.1).
 *
 * <p>Every method loads the campaign row with a lock ({@code findByIdForUpdate}) and checks the status and the
 * deadline only after that, so two calls on the same campaign never both succeed. Timer A covers the whole
 * negotiation: if the brand has not decided on a counter-offer by the response deadline, the request expires.
 */
@Service
@Transactional
public class CampaignResponseServiceImpl implements CampaignResponseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignResponseServiceImpl.class);

    /** The statuses Timer A watches: waiting for the creator, or waiting for the brand's decision. */
    private static final List<CampaignStatus> OPEN_FOR_RESPONSE =
            List.of(CampaignStatus.AWAITING_CREATOR_RESPONSE, CampaignStatus.COUNTERED);

    private final CampaignRepository campaignRepository;
    private final CounterOfferRepository counterOfferRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogger auditLogger;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public CampaignResponseServiceImpl(
            CampaignRepository campaignRepository,
            CounterOfferRepository counterOfferRepository,
            NotificationRepository notificationRepository,
            AuditLogger auditLogger,
            TransactionOperations transactions) {

        this(campaignRepository, counterOfferRepository, notificationRepository, auditLogger, transactions,
                Clock.systemUTC());
    }

    CampaignResponseServiceImpl(
            CampaignRepository campaignRepository,
            CounterOfferRepository counterOfferRepository,
            NotificationRepository notificationRepository,
            AuditLogger auditLogger,
            TransactionOperations transactions,
            Clock clock) {

        this.campaignRepository = campaignRepository;
        this.counterOfferRepository = counterOfferRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogger = auditLogger;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void acceptCampaign(Long creatorId, Long campaignId) {
        Campaign campaign = lockCampaignOfCreator(creatorId, campaignId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);

        campaign.accept(now);
        campaignRepository.save(campaign);
        auditLogger.record("CAMPAIGN_ACCEPTED", "campaign", campaignId);
        notifyUser(campaign.getBrandId(), campaign, Notification.CAMPAIGN_ACCEPTED,
                "The creator accepted your request for campaign " + campaign.getName() + ".");
    }

    @Override
    public void rejectCampaign(Long creatorId, Long campaignId) {
        Campaign campaign = lockCampaignOfCreator(creatorId, campaignId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);

        campaign.reject(now);
        campaignRepository.save(campaign);
        auditLogger.record("CAMPAIGN_REJECTED", "campaign", campaignId);
        notifyUser(campaign.getBrandId(), campaign, Notification.CAMPAIGN_REJECTED,
                "The creator rejected your request for campaign " + campaign.getName() + ".");
    }

    @Override
    public void submitCounterOffer(Long creatorId, Long campaignId, CounterOfferRequest request) {
        Campaign campaign = lockCampaignOfCreator(creatorId, campaignId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireResponseWindowOpen(campaign, now);

        if (request == null || (request.priceCents() == null && request.deliverables() == null)) {
            throw new ValidationFailedException("counterOffer", "You must change the price, the deliverables, or both.");
        }
        if (request.priceCents() != null && request.priceCents() <= 0) {
            throw new ValidationFailedException("priceCents", "The price must be more than 0.");
        }
        if (request.deliverables() != null && request.deliverables().isEmpty()) {
            throw new ValidationFailedException("deliverables", "At least one deliverable is required.");
        }

        // A counter-offer holds the full proposed terms, so what she did not change is copied from the request.
        List<DeliverableItem> current = describe(campaign.getDeliverables());
        List<DeliverableItem> proposed = request.deliverables() != null ? normalise(request.deliverables()) : current;
        Long price = request.priceCents() != null ? request.priceCents() : campaign.getPriceOfferedCents();

        if (proposed.isEmpty()) {
            throw new ValidationFailedException("deliverables", "The campaign must have at least one deliverable.");
        }
        if (Objects.equals(price, campaign.getPriceOfferedCents()) && sameDeliverables(current, proposed)) {
            throw new ValidationFailedException("counterOffer",
                    "A counter-offer must change the price, the deliverables, or both.");
        }

        campaign.submitCounterOffer(now);
        campaignRepository.save(campaign);
        try {
            // The unique campaign_id in counter_offer is the final authority on "one counter-offer per campaign".
            counterOfferRepository.saveAndFlush(new CounterOffer(campaignId, price, proposed));
        } catch (DataIntegrityViolationException e) {
            throw new CampaignResponseStateException("This request already has a counter-offer.");
        }
        auditLogger.record("COUNTER_OFFER_SUBMITTED", "campaign", campaignId);
        notifyUser(campaign.getBrandId(), campaign, Notification.COUNTER_OFFER_RECEIVED,
                "The creator made a counter-offer on campaign " + campaign.getName()
                        + ". Approve or decline it before the deadline.");
    }

    @Override
    public void approveCounterOffer(Long brandId, Long campaignId) {
        Campaign campaign = lockCampaignOfBrand(brandId, campaignId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.COUNTERED);
        requireResponseWindowOpen(campaign, now);

        CounterOffer counterOffer = findCounterOffer(campaignId);
        counterOffer.approve(now);
        campaign.approveCounterOffer(counterOffer.getPriceCents(), now);
        campaign.replaceDeliverables(counterOffer.getDeliverables().stream()
                .map(item -> new CampaignDeliverable(
                        parseEnum(DeliverablePlatform.class, item.platform()),
                        parseEnum(DeliverableType.class, item.type()),
                        item.count()))
                .toList());

        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);
        auditLogger.record("COUNTER_OFFER_APPROVED", "campaign", campaignId);
        notifyUser(campaign.getCreatorId(), campaign, Notification.COUNTER_OFFER_APPROVED,
                "Your counter-offer for campaign " + campaign.getName() + " was approved.");
    }

    @Override
    public void declineCounterOffer(Long brandId, Long campaignId) {
        Campaign campaign = lockCampaignOfBrand(brandId, campaignId);

        Instant now = clock.instant();
        requireStatus(campaign, CampaignStatus.COUNTERED);
        requireResponseWindowOpen(campaign, now);

        CounterOffer counterOffer = findCounterOffer(campaignId);
        counterOffer.decline(now);
        campaign.declineCounterOffer(now);

        campaignRepository.save(campaign);
        counterOfferRepository.save(counterOffer);
        auditLogger.record("COUNTER_OFFER_DECLINED", "campaign", campaignId);
        notifyUser(campaign.getCreatorId(), campaign, Notification.COUNTER_OFFER_DECLINED,
                "Your counter-offer for campaign " + campaign.getName() + " was declined.");
    }

    /**
     * Timer A. Runs outside any transaction: each overdue campaign is expired in its own short transaction, so
     * one bad row cannot undo the others, and a second run that overlaps this one waits on the row lock and then
     * finds the campaign already expired.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int expireOverdueCampaignResponses() {
        Instant now = clock.instant();
        int expired = 0;

        for (Long campaignId : campaignRepository.findOverdueResponseIds(OPEN_FOR_RESPONSE, now)) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> expireOne(campaignId, now)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Could not expire campaign {}; it will be tried again on the next run.", campaignId, e);
            }
        }
        return expired;
    }

    /** One campaign, in its own transaction. False when it is no longer overdue, e.g. it was answered meanwhile. */
    private boolean expireOne(Long campaignId, Instant now) {
        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId).orElse(null);
        if (campaign == null
                || !OPEN_FOR_RESPONSE.contains(campaign.getStatus())
                || campaign.getResponseDeadline() == null
                || campaign.getResponseDeadline().isAfter(now)) {
            return false;
        }

        boolean countered = campaign.getStatus() == CampaignStatus.COUNTERED;
        campaign.expireResponse(now);
        campaignRepository.save(campaign);
        auditLogger.record("CAMPAIGN_RESPONSE_EXPIRED", "campaign", campaignId);

        notifyUser(campaign.getBrandId(), campaign, Notification.CAMPAIGN_RESPONSE_EXPIRED,
                countered
                        ? "Campaign " + campaign.getName() + " expired: you did not decide on the counter-offer in time."
                        : "The creator did not respond to campaign " + campaign.getName()
                                + " before the response deadline.");
        if (countered) {
            notifyUser(campaign.getCreatorId(), campaign, Notification.CAMPAIGN_RESPONSE_EXPIRED,
                    "Campaign " + campaign.getName() + " expired: the brand did not decide on your counter-offer in time.");
        }
        return true;
    }

    private void notifyUser(Long userId, Campaign campaign, String type, String message) {
        notificationRepository.save(new Notification(userId, campaign.getId(), type, message));
    }

    /** The deliverables as the counter-offer stores them: platform and type as upper-case names. */
    private static List<DeliverableItem> describe(List<CampaignDeliverable> deliverables) {
        return deliverables.stream()
                .map(item -> new DeliverableItem(item.getPlatform().name(), item.getType().name(), item.getCount()))
                .toList();
    }

    private static boolean sameDeliverables(List<DeliverableItem> a, List<DeliverableItem> b) {
        return keys(a).equals(keys(b));
    }

    private static List<String> keys(List<DeliverableItem> items) {
        return items.stream()
                .map(item -> item.platform() + ":" + item.type() + ":" + item.count())
                .sorted()
                .toList();
    }

    /** Checks each proposed deliverable and stores platform and type the way the campaign does (upper case). */
    private static List<DeliverableItem> normalise(List<DeliverableItem> items) {
        List<DeliverableItem> result = new ArrayList<>();
        for (DeliverableItem item : items) {
            DeliverablePlatform platform = parseEnum(DeliverablePlatform.class, item.platform());
            DeliverableType type = parseEnum(DeliverableType.class, item.type());
            if (platform == null || type == null) {
                throw new ValidationFailedException("deliverables", "Each deliverable needs a known platform and type.");
            }
            if (item.count() == null || item.count() < 1) {
                throw new ValidationFailedException("deliverables", "Each deliverable needs a count of 1 or more.");
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

    /**
     * The creator's own request, locked. A draft, or a campaign of someone else, is "not found": the two look the
     * same, so a creator cannot find out that a draft naming her exists.
     */
    private Campaign lockCampaignOfCreator(Long creatorId, Long campaignId) {
        return campaignRepository.findByIdForUpdate(campaignId)
                .filter(campaign -> campaign.getStatus() != CampaignStatus.DRAFT
                        && Objects.equals(campaign.getCreatorId(), creatorId))
                .orElseThrow(CampaignNotFoundException::new);
    }

    /** The brand's own campaign, locked; another brand's campaign is "not found". */
    private Campaign lockCampaignOfBrand(Long brandId, Long campaignId) {
        return campaignRepository.findByIdForUpdate(campaignId)
                .filter(campaign -> Objects.equals(campaign.getBrandId(), brandId))
                .orElseThrow(CampaignNotFoundException::new);
    }

    private CounterOffer findCounterOffer(Long campaignId) {
        return counterOfferRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new CampaignResponseStateException("This campaign has no counter-offer to decide."));
    }

    private void requireStatus(Campaign campaign, CampaignStatus expected) {
        if (campaign.getStatus() != expected) {
            throw new CampaignResponseStateException(
                    "This can't be done: the campaign is " + campaign.getStatus() + ", not " + expected + ".");
        }
    }

    private void requireResponseWindowOpen(Campaign campaign, Instant now) {
        if (campaign.getResponseDeadline() == null || !campaign.getResponseDeadline().isAfter(now)) {
            throw new CampaignResponseStateException("The response deadline for this request has passed.");
        }
    }
}
