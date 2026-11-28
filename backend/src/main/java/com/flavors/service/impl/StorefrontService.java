package com.flavors.service.impl;

import com.flavors.dto.StorefrontResponse;
import com.flavors.entity.AppUser;
import com.flavors.entity.Branch;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.Offer;
import com.flavors.enums.UserStatus;
import com.flavors.enums.VerificationStatus;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.BranchRepository;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.OfferRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** EEC-48: safe, anonymous storefront view using EEC-41's offer eligibility and metadata. */
@Service
@Transactional(readOnly = true)
public class StorefrontService {
    private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");
    private final CreatorProfileRepository creators;
    private final AppUserRepository users;
    private final OfferRepository offers;
    private final CampaignRepository campaigns;
    private final BranchRepository branches;
    private final BrandRepository brands;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public StorefrontService(CreatorProfileRepository creators, AppUserRepository users, OfferRepository offers,
                             CampaignRepository campaigns, BranchRepository branches, BrandRepository brands,
                             @Value("${flavors.campaign.zone:Asia/Beirut}") String zone) {
        this(creators, users, offers, campaigns, branches, brands, Clock.systemUTC(), ZoneId.of(zone));
    }

    /** Clock injection allows deterministic expiry and cap tests. */
    StorefrontService(CreatorProfileRepository creators, AppUserRepository users, OfferRepository offers,
                      CampaignRepository campaigns, BranchRepository branches, BrandRepository brands,
                      Clock clock, ZoneId zone) {
        this.creators = creators;
        this.users = users;
        this.offers = offers;
        this.campaigns = campaigns;
        this.branches = branches;
        this.brands = brands;
        this.clock = clock;
        this.zone = zone;
    }

    public StorefrontResponse publicStorefront(String slug) {
        if (slug == null || !slug.matches("[a-z0-9-]{1,80}")) throw missing();
        return view(creators.findByStorefrontSlug(slug).orElseThrow(StorefrontService::missing));
    }

    public StorefrontResponse ownStorefront(long userId) {
        return view(creators.findById(userId).orElseThrow(StorefrontService::missing));
    }

    private StorefrontResponse view(CreatorProfile profile) {
        AppUser account = users.findById(profile.getUserId()).orElseThrow(StorefrontService::missing);
        if (profile.getVerificationStatus() != VerificationStatus.APPROVED
                || account.getStatus() != UserStatus.ACTIVE
                || profile.getStorefrontSlug() == null) {
            throw missing();
        }
        var now = clock.instant();
        List<StorefrontResponse.LiveOffer> live = offers.findPublicCandidates(profile.getUserId(), now).stream()
                .filter(offer -> offer.getExpiresAt().isAfter(now))
                .filter(offer -> offer.getMaxRedemptions() == null
                        || offers.countRedemptions(offer.getId()) < offer.getMaxRedemptions())
                .map(this::publicOffer)
                .toList();
        return new StorefrontResponse(profile.getDisplayName(), profile.getBio(), profile.getFoodCategory(),
                profile.getLocation(), profile.getStorefrontSlug(), live);
    }

    private StorefrontResponse.LiveOffer publicOffer(Offer offer) {
        var campaign = campaigns.findById(offer.getCampaignId()).orElseThrow(StorefrontService::missing);
        // Only expose branches owned by the campaign's restaurant, never branch IDs of another brand.
        List<Branch> all = branches.findByBrandIdOrderByIdAsc(campaign.getBrandId());
        List<StorefrontResponse.Location> locations = all.stream()
                .filter(branch -> offer.isAllBranches() || offer.getBranchIds().contains(branch.getId()))
                .map(branch -> new StorefrontResponse.Location(branch.getName(), branch.getLocation()))
                .toList();
        String restaurant = brands.findById(campaign.getBrandId())
                .map(brand -> brand.getBusinessName()).orElse("Restaurant");
        LocalDate expiresOn = LocalDate.ofInstant(offer.getExpiresAt(), zone).minusDays(1);
        return new StorefrontResponse.LiveOffer(offer.getId(), restaurant, offer.getDiscountPercent(), expiresOn,
                Offer.daysOf(offer.getValidDays()),
                offer.getValidFrom() == null ? null : offer.getValidFrom().format(HOURS),
                offer.getValidTo() == null ? null : offer.getValidTo().format(HOURS),
                offer.getMinPurchaseCents(), offer.getPerPersonLimit(), locations);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Storefront not found");
    }
}
