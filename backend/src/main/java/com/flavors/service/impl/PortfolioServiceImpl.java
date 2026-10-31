package com.flavors.service.impl;
 
import static java.util.stream.Collectors.toSet;
 
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.PortfolioFeature;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.InvalidPortfolioException;
import com.flavors.exception.PortfolioNotAvailableException;
import com.flavors.mapper.PortfolioMapper;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.PortfolioFeatureRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.PortfolioService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
 
@Service
@Transactional
class PortfolioServiceImpl implements PortfolioService {
 
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
 
        if (request.priceMinCents() == null || request.priceMaxCents() == null) {
            throw new InvalidPortfolioException("Price range is required.");
        }
        try {
            profile.updateDetails(request.bio(), request.foodCategory(), request.location(),
                    request.priceMinCents(), request.priceMaxCents());
        } catch (IllegalArgumentException e) {
            // EX-F1.2.2: the rule text comes from the entity.
            throw new InvalidPortfolioException(e.getMessage());
        }
 
        replaceFeatured(creatorId, request.featuredCampaignIds());
        // Any exception above rolls the whole transaction back, so nothing is half-saved.
        return build(profile);
    }
 
    /** Only an approved creator has a portfolio (FR-F20.4). */
    private CreatorProfile approvedProfile(long creatorId) {
        CreatorProfile profile = profiles.findById(creatorId).orElseThrow(PortfolioNotAvailableException::new);
        if (profile.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new PortfolioNotAvailableException();
        }
        return profile;
    }
 
    private List<Campaign> completedCampaigns(long creatorId) {
        return campaigns.findByCreatorIdAndStatus(creatorId, CampaignStatus.COMPLETED);
    }
 
    /** Makes the featured list equal to {@code requested}: removes the others, adds the new ones. */
    private void replaceFeatured(long creatorId, List<Long> requested) {
        Set<Long> wanted = requested == null ? new HashSet<>() : new HashSet<>(requested);
 
        // EX-F1.2.1: only her own completed campaigns can be featured.
        Set<Long> completedIds = completedCampaigns(creatorId).stream().map(Campaign::getId).collect(toSet());
        if (!completedIds.containsAll(wanted)) {
            throw new InvalidPortfolioException("Only your completed campaigns can be featured.");
        }
 
        List<PortfolioFeature> current = features.findByCreatorId(creatorId);
        Set<Long> alreadyFeatured = current.stream().map(PortfolioFeature::getCampaignId).collect(toSet());
 
        features.deleteAll(current.stream().filter(f -> !wanted.contains(f.getCampaignId())).toList());
        features.saveAll(wanted.stream()
                .filter(id -> !alreadyFeatured.contains(id))
                .map(id -> new PortfolioFeature(id, creatorId))
                .toList());
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