package com.flavors.service.impl;

import com.flavors.dto.CampaignCreatorResponse;
import com.flavors.dto.CampaignRequest;
import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CampaignSummaryResponse;
import com.flavors.entity.Branch;
import com.flavors.entity.Brand;
import com.flavors.entity.Campaign;
import com.flavors.entity.CampaignDeliverable;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.Notification;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import com.flavors.enums.UserStatus;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.CampaignNotEditableException;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignValidationException;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.exception.NoBranchException;
import com.flavors.mapper.CampaignMapper;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.BranchRepository;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.NotificationRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.CampaignService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaigns;
    private final BranchRepository branches;
    private final BrandRepository brands;
    private final CreatorProfileRepository creators;
    private final AppUserRepository users;
    private final NotificationRepository notifications;
    private final CampaignMapper mapper;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ZoneId zone;

    /**
     * @param zone the time zone "today" is read in for the start date rule; the brands and creators are in
     *             Lebanon, and the UTC date would still be yesterday for the first hours of the local day
     */
    @Autowired
    public CampaignServiceImpl(CampaignRepository campaigns, BranchRepository branches, BrandRepository brands,
                               CreatorProfileRepository creators, AppUserRepository users,
                               NotificationRepository notifications, CampaignMapper mapper, AuditLogger auditLogger,
                               @Value("${flavors.campaign.zone:Asia/Beirut}") String zone) {
        this(campaigns, branches, brands, creators, users, notifications, mapper, auditLogger, Clock.systemUTC(),
                ZoneId.of(zone));
    }

    CampaignServiceImpl(CampaignRepository campaigns, BranchRepository branches, BrandRepository brands,
                        CreatorProfileRepository creators, AppUserRepository users,
                        NotificationRepository notifications, CampaignMapper mapper, AuditLogger auditLogger,
                        Clock clock, ZoneId zone) {
        this.campaigns = campaigns;
        this.branches = branches;
        this.brands = brands;
        this.creators = creators;
        this.users = users;
        this.notifications = notifications;
        this.mapper = mapper;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> list(long brandId) {
        List<Campaign> mine = campaigns.findByBrandIdOrderByUpdatedAtDescIdDesc(brandId);
        Map<Long, CreatorProfile> creatorsById = creators
                .findAllById(mine.stream().map(Campaign::getCreatorId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CreatorProfile::getUserId, Function.identity()));
        return mine.stream()
                .map(c -> mapper.toSummary(c, creatorsById.get(c.getCreatorId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResponse get(long brandId, long campaignId) {
        Campaign campaign = campaigns.findByIdAndBrandId(campaignId, brandId).orElseThrow(CampaignNotFoundException::new);
        return toResponse(campaign);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignCreatorResponse findCreator(long creatorId) {
        CreatorProfile creator = usableCreator(creatorId).orElseThrow(() -> new CreatorNotFoundException(creatorId));
        return new CampaignCreatorResponse(creator.getUserId(), creator.getDisplayName(), creator.getFoodCategory(),
                creator.getLocation());
    }

    @Override
    @Transactional
    public CampaignResponse saveDraft(long brandId, CampaignRequest request) {
        requireBranch(brandId);
        Checked checked = check(brandId, request, false);
        Campaign campaign = new Campaign(brandId, checked.name(), clock.instant());
        fill(campaign, checked);
        campaigns.save(campaign);
        auditLogger.record("CAMPAIGN_DRAFT_SAVED", "campaign", campaign.getId());
        return toResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse updateDraft(long brandId, long campaignId, CampaignRequest request) {
        requireBranch(brandId);
        Campaign campaign = campaigns.findByIdAndBrandIdForUpdate(campaignId, brandId)
                .orElseThrow(CampaignNotFoundException::new);
        if (!campaign.isDraft()) {
            throw new CampaignNotEditableException();
        }
        fill(campaign, check(brandId, request, false));
        auditLogger.record("CAMPAIGN_DRAFT_UPDATED", "campaign", campaign.getId());
        return toResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse send(long brandId, CampaignRequest request) {
        requireBranch(brandId);
        // Checked before anything is saved: a refused send must leave no campaign behind (EX-F3.1.1).
        Checked checked = check(brandId, request, true);
        Campaign campaign = new Campaign(brandId, checked.name(), clock.instant());
        fill(campaign, checked);
        campaigns.save(campaign);
        return sendNow(brandId, campaign);
    }

    @Override
    @Transactional
    public CampaignResponse send(long brandId, long campaignId, CampaignRequest request) {
        requireBranch(brandId);
        Campaign campaign = campaigns.findByIdAndBrandIdForUpdate(campaignId, brandId)
                .orElseThrow(CampaignNotFoundException::new);
        if (!campaign.isDraft()) {
            throw new CampaignNotEditableException();
        }
        fill(campaign, check(brandId, request, true));
        return sendNow(brandId, campaign);
    }

    /** Marks the campaign sent and tells the creator, in the caller's transaction. */
    private CampaignResponse sendNow(long brandId, Campaign campaign) {
        Instant now = clock.instant();
        campaign.send(now);
        // The creator hears about it in the app only, with the id of the campaign to open (EEC-43 shows it to her).
        String brandName = brands.findById(brandId).map(Brand::getBusinessName).orElse("A brand");
        notifications.save(new Notification(campaign.getCreatorId(), campaign.getId(), Notification.CAMPAIGN_REQUEST,
                brandName + " sent you a campaign request: " + campaign.getName()));
        auditLogger.record("CAMPAIGN_SENT", "campaign", campaign.getId(), Map.of(
                "creatorId", campaign.getCreatorId(),
                "priceOfferedCents", campaign.getPriceOfferedCents(),
                "responseDeadline", campaign.getResponseDeadline().toString()));
        return toResponse(campaign);
    }

    private void requireBranch(long brandId) {
        if (branches.findByBrandId(brandId).isEmpty()) {
            throw new NoBranchException();
        }
    }

    private CampaignResponse toResponse(Campaign campaign) {
        CreatorProfile creator = campaign.getCreatorId() == null ? null
                : creators.findById(campaign.getCreatorId()).orElse(null);
        return mapper.toResponse(campaign, creator, branches.findAllById(campaign.getBranchIds()));
    }

    private void fill(Campaign campaign, Checked c) {
        campaign.saveDraft(c.name(), c.creatorId(), c.priceOfferedCents(), c.startDate(), c.lastPostingDate(),
                c.contentRequirements(), c.branchIds(), c.deliverables(), clock.instant());
    }

    /** The creator, if she can receive requests: approved by an admin and not suspended. */
    private java.util.Optional<CreatorProfile> usableCreator(long creatorId) {
        return creators.findById(creatorId)
                .filter(c -> c.getVerificationStatus() == VerificationStatus.APPROVED)
                .filter(c -> users.findById(c.getUserId()).map(u -> u.getStatus() == UserStatus.ACTIVE).orElse(false));
    }

    /** The request after trimming and parsing; only produced when every rule passed. */
    private record Checked(String name, Long creatorId, Long priceOfferedCents, LocalDate startDate,
                           LocalDate lastPostingDate, String contentRequirements, Set<Long> branchIds,
                           List<CampaignDeliverable> deliverables) {
    }

    /**
     * Applies the field rules. A draft only needs a name, but whatever it does contain must be sensible;
     * a send also needs everything else (FR-F3.1, FR-F3.3). All wrong fields are reported together, so the
     * form can highlight them at once.
     */
    private Checked check(long brandId, CampaignRequest r, boolean forSend) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty()) {
            errors.put("name", "Enter a campaign name.");
        } else if (name.length() > Campaign.MAX_NAME_LENGTH) {
            errors.put("name", "Use at most " + Campaign.MAX_NAME_LENGTH + " characters.");
        }

        Long creatorId = r.creatorId();
        if (creatorId == null) {
            if (forSend) errors.put("creatorId", "Choose a creator.");
        } else if (usableCreator(creatorId).isEmpty()) {
            errors.put("creatorId", "Choose an approved creator.");
        }

        Set<Long> branchIds = new LinkedHashSet<>(r.branchIds() == null ? List.of() : r.branchIds());
        if (branchIds.isEmpty()) {
            if (forSend) errors.put("branchIds", "Pick at least one branch.");
        } else {
            // Other brands' branches are refused with the same words as an unknown id.
            Set<Long> own = branches.findByBrandId(brandId).stream().map(Branch::getId).collect(Collectors.toSet());
            if (branchIds.contains(null) || !own.containsAll(branchIds)) {
                errors.put("branchIds", "Pick only your own branches.");
            }
        }

        Long price = r.priceOfferedCents();
        if (price == null) {
            if (forSend) errors.put("priceOfferedCents", "Enter the price you offer.");
        } else if (price <= 0) {
            errors.put("priceOfferedCents", "The price must be above 0.");
        }

        LocalDate start = r.startDate();
        LocalDate last = r.lastPostingDate();
        if (start == null) {
            if (forSend) errors.put("startDate", "Pick a start date.");
        } else if (forSend && start.isBefore(LocalDate.now(clock.withZone(zone)))) {
            // Not checked for a draft: it may be saved days before it is sent, and is checked again then.
            errors.put("startDate", "The start date can't be in the past.");
        }
        if (last == null) {
            if (forSend) errors.put("lastPostingDate", "Pick a last posting date.");
        } else if (start != null && last.isBefore(start)) {
            errors.put("lastPostingDate", "The last posting date must be on or after the start date.");
        }

        List<CampaignDeliverable> deliverables = new ArrayList<>();
        List<CampaignRequest.DeliverableRequest> rows = r.deliverables() == null ? List.of() : r.deliverables();
        if (rows.isEmpty() && forSend) {
            errors.put("deliverables", "Add at least one deliverable.");
        }
        for (int i = 0; i < rows.size(); i++) {
            CampaignRequest.DeliverableRequest row = rows.get(i);
            DeliverablePlatform platform = row == null ? null : parse(DeliverablePlatform.class, row.platform());
            DeliverableType type = row == null ? null : parse(DeliverableType.class, row.type());
            if (platform == null) errors.put("deliverables[" + i + "].platform", "Choose a platform.");
            if (type == null) errors.put("deliverables[" + i + "].type", "Choose a type.");
            if (row == null || row.count() == null || row.count() < 1) {
                errors.put("deliverables[" + i + "].count", "Enter 1 or more.");
            } else if (platform != null && type != null) {
                deliverables.add(new CampaignDeliverable(platform, type, row.count()));
            }
        }

        String content = r.contentRequirements() == null || r.contentRequirements().isBlank()
                ? null : r.contentRequirements().strip();
        if (content != null && content.length() > Campaign.MAX_CONTENT_REQUIREMENTS_LENGTH) {
            errors.put("contentRequirements", "Use at most " + Campaign.MAX_CONTENT_REQUIREMENTS_LENGTH + " characters.");
        }

        if (!errors.isEmpty()) {
            throw new CampaignValidationException(errors);
        }
        return new Checked(name, creatorId, price, start, last, content, branchIds, deliverables);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String text) {
        if (text == null) return null;
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
