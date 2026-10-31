package com.flavors.service.impl;

import static java.util.stream.Collectors.toSet;

import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.PortfolioFeature;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.AlreadyFeaturedException;
import com.flavors.exception.FeatureNotFoundException;
import com.flavors.exception.InvalidPortfolioException;
import com.flavors.exception.PortfolioNotAvailableException;
import com.flavors.mapper.PortfolioMapper;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.PortfolioFeatureRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.PortfolioService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PortfolioServiceImpl implements PortfolioService {

    private static final Set<String> PATCHABLE_FIELDS =
            Set.of("bio", "foodCategory", "location", "priceMinCents", "priceMaxCents");

    private static final String ONLY_COMPLETED = "Only your completed campaigns can be featured.";

    private final CreatorProfileRepository profiles;
    private final SocialAccountRepository socialAccounts;
    private final CampaignRepository campaigns;
    private final PortfolioFeatureRepository features;
    private final PortfolioMapper mapper;

    PortfolioServiceImpl(CreatorProfileRepository profiles, SocialAccountRepository socialAccounts,
                         CampaignRepository campaigns, PortfolioFeatureRepository features,
                         PortfolioMapper mapper) {
        this.profiles = profiles;
        this.socialAccounts = socialAccounts;
        this.campaigns = campaigns;
        this.features = features;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponse getMyPortfolio(long creatorId) {
        return build(approvedProfile(creatorId));
    }

    @Override
    public PortfolioResponse updateMyPortfolio(long creatorId, UpdatePortfolioRequest request) {
        CreatorProfile profile = approvedProfile(creatorId);
        applyDetails(profile, request.bio(), request.foodCategory(), request.location(),
                request.priceMinCents(), request.priceMaxCents());
        return build(profile);
    }

    @Override
    public PortfolioResponse patchMyPortfolio(long creatorId, Map<String, Object> changes) {
        CreatorProfile profile = approvedProfile(creatorId);

        if (changes == null || changes.isEmpty()) {
            throw new InvalidPortfolioException("Send at least one field to change.");
        }
        // A misspelled field must not turn into a silent no-op.
        Set<String> unknown = new TreeSet<>(changes.keySet());
        unknown.removeAll(PATCHABLE_FIELDS);
        if (!unknown.isEmpty()) {
            throw new InvalidPortfolioException("Unknown field: " + String.join(", ", unknown) + ".");
        }

        // A field that is not sent keeps its stored value; the merged result is validated as a whole.
        String bio = changes.containsKey("bio") ? text(changes.get("bio"), "bio") : profile.getBio();
        String category = changes.containsKey("foodCategory")
                ? text(changes.get("foodCategory"), "foodCategory") : profile.getFoodCategory();
        String location = changes.containsKey("location")
                ? text(changes.get("location"), "location") : profile.getLocation();
        Long min = changes.containsKey("priceMinCents")
                ? cents(changes.get("priceMinCents"), "priceMinCents") : Long.valueOf(profile.getPriceMinCents());
        Long max = changes.containsKey("priceMaxCents")
                ? cents(changes.get("priceMaxCents"), "priceMaxCents") : Long.valueOf(profile.getPriceMaxCents());

        applyDetails(profile, bio, category, location, min, max);
        return build(profile);
    }

    @Override
    public PortfolioResponse.CampaignView featureCampaign(long creatorId, Long campaignId) {
        approvedProfile(creatorId);
        if (campaignId == null) {
            throw new InvalidPortfolioException("campaignId is required.");
        }

        // EX-F1.2.1: it must be her own COMPLETED campaign. The same message covers "does not exist",
        // so the answer does not reveal other creators' campaigns.
        Campaign campaign = campaigns.findById(campaignId)
                .filter(c -> Long.valueOf(creatorId).equals(c.getCreatorId()))
                .filter(c -> c.getStatus() == CampaignStatus.COMPLETED)
                .orElseThrow(() -> new InvalidPortfolioException(ONLY_COMPLETED));

        if (features.existsById(campaignId)) {
            throw new AlreadyFeaturedException();
        }
        // Flush now: if a parallel request inserted the same row first, the primary key refuses this one
        // here, and the exception handler turns that into a 409.
        features.saveAndFlush(new PortfolioFeature(campaignId, creatorId));
        return new PortfolioResponse.CampaignView(campaign.getId(), campaign.getName(), true);
    }

    @Override
    public void unfeatureCampaign(long creatorId, long campaignId) {
        approvedProfile(creatorId);
        PortfolioFeature feature = features.findById(campaignId)
                .filter(f -> f.getCreatorId() == creatorId)
                .orElseThrow(FeatureNotFoundException::new);
        features.delete(feature);
    }

    /** Only an approved creator has a portfolio (FR-F20.4). */
    private CreatorProfile approvedProfile(long creatorId) {
        CreatorProfile profile = profiles.findById(creatorId).orElseThrow(PortfolioNotAvailableException::new);
        if (profile.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new PortfolioNotAvailableException();
        }
        return profile;
    }

    /** Shared by PUT and PATCH. An exception rolls the transaction back, so nothing is half-saved. */
    private void applyDetails(CreatorProfile profile, String bio, String category, String location,
                              Long priceMinCents, Long priceMaxCents) {
        if (priceMinCents == null || priceMaxCents == null) {
            throw new InvalidPortfolioException("Price range is required.");
        }
        try {
            profile.updateDetails(bio, category, location, priceMinCents, priceMaxCents);
        } catch (IllegalArgumentException e) {
            // EX-F1.2.2: the rule text comes from the entity.
            throw new InvalidPortfolioException(e.getMessage());
        }
    }

    private static String text(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        throw new InvalidPortfolioException(field + " must be text.");
    }

    private static Long cents(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        throw new InvalidPortfolioException(field + " must be a whole number of cents.");
    }

    private List<Campaign> completedCampaigns(long creatorId) {
        return campaigns.findByCreatorIdAndStatus(creatorId, CampaignStatus.COMPLETED);
    }

    private PortfolioResponse build(CreatorProfile profile) {
        long creatorId = profile.getUserId();
        Set<Long> featuredIds = features.findByCreatorId(creatorId).stream()
                .map(PortfolioFeature::getCampaignId)
                .collect(toSet());
        return mapper.toResponse(profile, socialAccounts.findByCreatorId(creatorId),
                completedCampaigns(creatorId), featuredIds);
    }
}