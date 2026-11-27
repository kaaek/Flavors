package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flavors.entity.AppUser;
import com.flavors.entity.Brand;
import com.flavors.entity.Branch;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.Offer;
import com.flavors.enums.UserStatus;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.BranchRepository;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.OfferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StorefrontServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private final CreatorProfileRepository creators = mock(CreatorProfileRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final BrandRepository brands = mock(BrandRepository.class);
    private final StorefrontService service = new StorefrontService(creators, users, offers, campaigns, branches, brands,
            Clock.fixed(NOW, ZoneId.of("UTC")), ZoneId.of("Asia/Beirut"));
    private CreatorProfile profile;

    @BeforeEach
    void approvedCreator() {
        profile = new CreatorProfile(7L, "Lara Eats", "Burgers", "Beirut", 0, 0);
        profile.approve(1L, "lara-eats", NOW);
        when(creators.findByStorefrontSlug("lara-eats")).thenReturn(Optional.of(profile));
        when(creators.findById(7L)).thenReturn(Optional.of(profile));
        AppUser user = mock(AppUser.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void anonymousStorefrontHasLiveOffersWithPublicConditionsOnly() {
        Offer offer = offer(11L, NOW.plusSeconds(86400), null);
        when(offers.findPublicCandidates(7L, NOW)).thenReturn(List.of(offer));
        Campaign campaign = mock(Campaign.class);
        when(campaign.getBrandId()).thenReturn(9L);
        when(campaigns.findById(11L)).thenReturn(Optional.of(campaign));
        when(brands.findById(9L)).thenReturn(Optional.of(new Brand(9L, "Burger House", "Boss", "123")));
        when(branches.findByBrandIdOrderByIdAsc(9L)).thenReturn(List.of(new Branch(9L, "Downtown", "Beirut")));

        var page = service.publicStorefront("lara-eats");
        assertThat(page.displayName()).isEqualTo("Lara Eats");
        assertThat(page.offers()).hasSize(1);
        assertThat(page.offers().getFirst().restaurant()).isEqualTo("Burger House");
        assertThat(page.offers().getFirst().discountPercent()).isEqualTo(20);
        assertThat(page.offers().getFirst().branches().getFirst().name()).isEqualTo("Downtown");
    }

    @Test
    void expiredAndFullyRedeemedOffersNeverAppearEvenIfQueried() {
        Offer full = offer(11L, NOW.plusSeconds(86400), 2);
        Offer expired = offer(12L, NOW, null);
        when(offers.findPublicCandidates(7L, NOW)).thenReturn(List.of(full, expired));
        when(offers.countRedemptions(11L)).thenReturn(2L);
        assertThat(service.publicStorefront("lara-eats").offers()).isEmpty();
    }

    @Test
    void noOffersGivesEmptyStorefrontNot404() {
        assertThat(service.publicStorefront("lara-eats").offers()).isEmpty();
    }

    @Test
    void unknownSlugAndUnapprovedOrSuspendedCreatorsAreHidden() {
        assertThatThrownBy(() -> service.publicStorefront("not-a-storefront"))
                .isInstanceOf(ResponseStatusException.class);
        AppUser suspended = mock(AppUser.class);
        when(suspended.getStatus()).thenReturn(UserStatus.SUSPENDED);
        when(users.findById(7L)).thenReturn(Optional.of(suspended));
        assertThatThrownBy(() -> service.publicStorefront("lara-eats"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static Offer offer(long id, Instant expires, Integer max) {
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn(id);
        when(offer.getCampaignId()).thenReturn(id);
        when(offer.getExpiresAt()).thenReturn(expires);
        when(offer.getMaxRedemptions()).thenReturn(max);
        when(offer.isAllBranches()).thenReturn(true);
        when(offer.getDiscountPercent()).thenReturn((short) 20);
        when(offer.getValidDays()).thenReturn(Offer.EVERY_DAY);
        when(offer.getPerPersonLimit()).thenReturn((short) 1);
        return offer;
    }
}
