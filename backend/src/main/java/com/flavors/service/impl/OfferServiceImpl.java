package com.flavors.service.impl;

import static java.util.stream.Collectors.toSet;

import com.flavors.dto.OfferRequest;
import com.flavors.dto.OfferResponse;
import com.flavors.entity.Branch;
import com.flavors.entity.Campaign;
import com.flavors.entity.Offer;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.OfferState;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.OfferLockedException;
import com.flavors.exception.OfferValidationException;
import com.flavors.mapper.OfferMapper;
import com.flavors.repository.BranchRepository;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.OfferRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.OfferService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class OfferServiceImpl implements OfferService {

    /**
     * Until the campaign is active the brand can still set up or change the offer. After that diners may already hold
     * QR codes for it, so changing the discount or the rules under them would be unfair.
     */
    static final Set<CampaignStatus> EDITABLE = EnumSet.of(CampaignStatus.DRAFT,
            CampaignStatus.AWAITING_CREATOR_RESPONSE, CampaignStatus.COUNTERED, CampaignStatus.AWAITING_PAYMENT);

    /**
     * Paid and under way, not finished: the offer works (FR-F10.4). The proof stages count, because followers come to the
     * restaurant after the creator has posted.
     */
    static final Set<CampaignStatus> RUNNING = EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PROOF_SUBMITTED,
            CampaignStatus.CORRECTIONS_REQUESTED);

    /** The campaign is over, or never happened: the offer has ended for good. */
    static final Set<CampaignStatus> OVER = EnumSet.of(CampaignStatus.COMPLETED, CampaignStatus.CANCELLED,
            CampaignStatus.REMOVED, CampaignStatus.REJECTED, CampaignStatus.DECLINED, CampaignStatus.EXPIRED);

    /** per_person_limit is a smallint; a real offer never needs more than this. */
    static final int MAX_PER_PERSON = 100;

    private final OfferRepository offers;
    private final CampaignRepository campaigns;
    private final BranchRepository branches;
    private final OfferMapper mapper;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ZoneId zone;

    /**
     * @param zone the restaurants' time zone: "today" for the expiry rule, and the midnight the offer stops at. The same
     *             setting as the campaign's start date rule.
     */
    @Autowired
    OfferServiceImpl(OfferRepository offers, CampaignRepository campaigns, BranchRepository branches,
                     OfferMapper mapper, AuditLogger auditLogger,
                     @Value("${flavors.campaign.zone:Asia/Beirut}") String zone) {
        this(offers, campaigns, branches, mapper, auditLogger, Clock.systemUTC(), ZoneId.of(zone));
    }

    OfferServiceImpl(OfferRepository offers, CampaignRepository campaigns, BranchRepository branches,
                     OfferMapper mapper, AuditLogger auditLogger, Clock clock, ZoneId zone) {
        this.offers = offers;
        this.campaigns = campaigns;
        this.branches = branches;
        this.mapper = mapper;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfferResponse> get(long brandId, long campaignId) {
        Campaign campaign = campaigns.findByIdAndBrandId(campaignId, brandId)
                .orElseThrow(CampaignNotFoundException::new);
        return offers.findByCampaignId(campaignId).map(offer -> toResponse(offer, campaign));
    }

    @Override
    public OfferResponse save(long brandId, long campaignId, OfferRequest request) {
        // The campaign row is locked (the same lock the payment takes to activate it), so the offer can't change
        // while the campaign goes active: one waits for the other, and this one then sees ACTIVE and refuses.
        // It also makes two first saves run one after the other, so the second updates instead of inserting twice.
        Campaign campaign = campaigns.findByIdAndBrandIdForUpdate(campaignId, brandId)
                .orElseThrow(CampaignNotFoundException::new);
        if (!EDITABLE.contains(campaign.getStatus())) {
            throw new OfferLockedException();
        }
        Checked checked = check(brandId, request);

        Optional<Offer> existing = offers.findByCampaignId(campaignId);
        Offer offer = existing.orElseGet(() -> new Offer(campaignId));
        offer.update(checked.discountPercent(), checked.expiresAt(), checked.validDays(), checked.validFrom(),
                checked.validTo(), checked.allBranches(), checked.branchIds(), checked.minPurchaseCents(),
                checked.maxRedemptions(), checked.perPersonLimit(), clock.instant());
        offer = offers.saveAndFlush(offer);

        auditLogger.record(existing.isPresent() ? "OFFER_UPDATED" : "OFFER_CREATED", "campaign", campaignId,
                Map.of("offerId", offer.getId(), "discountPercent", (int) checked.discountPercent()));
        return toResponse(offer, campaign);
    }

    /**
     * Whether the offer can be used now (FR-F10.4): only while the campaign runs, before the expiry and below the
     * maximum. A campaign that is over wins over everything else, so an old offer reads "ended", not "expired".
     */
    static OfferState stateOf(CampaignStatus status, Instant expiresAt, Integer maxRedemptions, long redemptions,
                              Instant now) {
        if (OVER.contains(status)) {
            return OfferState.ENDED;
        }
        if (!now.isBefore(expiresAt)) {
            return OfferState.EXPIRED;
        }
        if (maxRedemptions != null && redemptions >= maxRedemptions) {
            return OfferState.FULL;
        }
        if (RUNNING.contains(status)) {
            return OfferState.LIVE;
        }
        if (status == CampaignStatus.DISPUTED) {
            return OfferState.PAUSED;
        }
        return OfferState.NOT_LIVE_YET;
    }

    private OfferResponse toResponse(Offer offer, Campaign campaign) {
        long redemptions = offers.countRedemptions(offer.getId());
        OfferState state = stateOf(campaign.getStatus(), offer.getExpiresAt(), offer.getMaxRedemptions(), redemptions,
                clock.instant());
        return mapper.toResponse(offer, campaign, zone, redemptions, state, EDITABLE.contains(campaign.getStatus()));
    }

    /** Every field rule at once, so the form can highlight all the wrong fields together (EX-F10.1.3). */
    private Checked check(long brandId, OfferRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();

        Integer discount = r.discountPercent();
        if (discount == null) {
            errors.put("discountPercent", "Enter the discount.");
        } else if (discount < 1 || discount > 100) {
            errors.put("discountPercent", "Enter a discount from 1 to 100%.");
        }

        LocalDate expiresOn = r.expiresOn();
        if (expiresOn == null) {
            errors.put("expiresOn", "Pick an expiry date.");
        } else if (expiresOn.isBefore(LocalDate.now(clock.withZone(zone)))) {
            errors.put("expiresOn", "The expiry date can't be in the past.");
        }

        short validDays = Offer.EVERY_DAY;
        if (r.validDays() != null) {
            if (r.validDays().isEmpty() || r.validDays().stream().anyMatch(Objects::isNull)) {
                errors.put("validDays", "Pick at least one day.");
            } else {
                validDays = Offer.bitsOf(r.validDays());
            }
        }

        boolean fromGiven = !blank(r.validFrom());
        boolean toGiven = !blank(r.validTo());
        LocalTime from = time(r.validFrom(), "validFrom", errors);
        LocalTime to = time(r.validTo(), "validTo", errors);
        if (fromGiven != toGiven) {
            errors.putIfAbsent(fromGiven ? "validTo" : "validFrom",
                    "Enter both times, or leave both empty for all day.");
        } else if (from != null && to != null && !from.isBefore(to)) {
            errors.put("validTo", "The end time must be after the start time. Hours past midnight aren't supported yet.");
        }

        boolean allBranches = r.allBranches() == null || r.allBranches();
        List<Long> branchIds = r.branchIds() == null ? List.of() : r.branchIds();
        if (!allBranches) {
            if (branchIds.isEmpty() || branchIds.stream().anyMatch(Objects::isNull)) {
                errors.put("branchIds", "Pick at least one branch, or choose all branches.");
            } else {
                Set<Long> own = branches.findByBrandId(brandId).stream().map(Branch::getId).collect(toSet());
                if (!own.containsAll(branchIds)) {
                    // EX-F10.1.2 limits the offer to the chosen branches, so they have to be the brand's own.
                    errors.put("branchIds", "Pick only your own branches.");
                }
            }
        }

        long minPurchaseCents = r.minPurchaseCents() == null ? 0 : r.minPurchaseCents();
        if (minPurchaseCents < 0) {
            errors.put("minPurchaseCents", "The minimum purchase can't be negative.");
        }

        Integer maxRedemptions = r.maxRedemptions();
        if (maxRedemptions != null && maxRedemptions < 1) {
            errors.put("maxRedemptions", "Enter 1 or more, or leave it empty for no maximum.");
        }

        // EX-F10.1.1: no limit given means one redemption per person per campaign.
        int perPersonLimit = r.perPersonLimit() == null ? 1 : r.perPersonLimit();
        if (perPersonLimit < 1) {
            errors.put("perPersonLimit", "Enter 1 or more.");
        } else if (perPersonLimit > MAX_PER_PERSON) {
            errors.put("perPersonLimit", "Use at most " + MAX_PER_PERSON + ".");
        }

        if (!errors.isEmpty()) {
            throw new OfferValidationException(errors);
        }
        // The offer works through the whole expiry day, so it stops at the next midnight in the restaurant's zone.
        Instant expiresAt = expiresOn.plusDays(1).atStartOfDay(zone).toInstant();
        return new Checked(discount.shortValue(), expiresAt, validDays, from, to, allBranches, branchIds,
                minPurchaseCents, maxRedemptions, (short) perPersonLimit);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * "14:30" as a time; empty means none. Only whole minutes are accepted ("14:30:00" is the same time): the page
     * shows and sends hours and minutes, so seconds would be lost the next time the offer is saved.
     */
    private static LocalTime time(String text, String field, Map<String, String> errors) {
        if (blank(text)) {
            return null;
        }
        LocalTime time;
        try {
            time = LocalTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            errors.put(field, "Enter a time like 14:30.");
            return null;
        }
        if (time.getSecond() != 0 || time.getNano() != 0) {
            errors.put(field, "Use hours and minutes only, like 14:30.");
            return null;
        }
        return time;
    }

    private record Checked(short discountPercent, Instant expiresAt, short validDays, LocalTime validFrom,
                           LocalTime validTo, boolean allBranches, List<Long> branchIds, long minPurchaseCents,
                           Integer maxRedemptions, short perPersonLimit) {
    }
}
