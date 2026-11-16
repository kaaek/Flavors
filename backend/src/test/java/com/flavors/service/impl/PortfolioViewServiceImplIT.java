package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CreatorPortfolioResponse;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.Role;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.PortfolioViewService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** What a brand sees on a creator's verified portfolio (UC-F1.1), run against the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PortfolioViewServiceImplIT {

    @Autowired
    PortfolioViewService service;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository profiles;

    @Autowired
    JdbcTemplate jdbc;

    private long newCreator(boolean approved) {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        if (approved) {
            jdbc.update("UPDATE creator_profile SET verification_status = 'APPROVED' WHERE user_id = ?", id);
        }
        return id;
    }

    private long newBrand() {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.BRAND)).getId();
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Burger Co', 'Karim', '70000000')", id);
        return id;
    }

    private long newCampaign(long brand, long creator, String status, long agreedPriceCents) {
        return jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                                      start_date, last_posting_date)
                VALUES (?, ?, 'Summer burgers', ?, ?, ?, current_date - 10, current_date - 3)
                RETURNING id
                """, Long.class, brand, creator, status, agreedPriceCents, agreedPriceCents);
    }

    private void feature(long campaign, long creator) {
        jdbc.update("INSERT INTO portfolio_feature (campaign_id, creator_id) VALUES (?, ?)", campaign, creator);
    }

    private long newOffer(long campaign) {
        return jdbc.queryForObject("""
                INSERT INTO offer (campaign_id, discount_percent, expires_at)
                VALUES (?, 10, now() + interval '30 days')
                RETURNING id
                """, Long.class, campaign);
    }

    private long newBranch(long brand) {
        return jdbc.queryForObject(
                "INSERT INTO branch (brand_id, name, location) VALUES (?, 'Hamra', 'Beirut') RETURNING id", Long.class, brand);
    }

    /** A redemption at the till: the bill before the discount, and the discount given. */
    private void redeem(long offer, long brand, long creator, long campaign, long branch, long billCents, long discountCents) {
        long claim = jdbc.queryForObject("""
                INSERT INTO offer_claim (offer_id, phone_enc, phone_hash, consent_at, qr_token, status)
                VALUES (?, ?, ?, now(), ?, 'REDEEMED')
                RETURNING id
                """, Long.class, offer, new byte[] {1}, UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("""
                INSERT INTO redemption (claim_id, offer_id, campaign_id, creator_id, brand_id, branch_id, bill_cents, discount_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, claim, offer, campaign, creator, brand, branch, billCents, discountCents);
    }

    private void review(long campaign, int rating, String text, int daysAgo) {
        jdbc.update("INSERT INTO review (campaign_id, rating, text, created_at) VALUES (?, ?, ?, now() - ? * interval '1 day')",
                campaign, rating, text, daysAgo);
    }

    // TC-F1.1.1
    @Test
    void showsProfileSocialNumbersFeaturedCampaignsAndRatings() {
        long lara = newCreator(true);
        long brand = newBrand();
        long branch = newBranch(brand);
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, followers, engagement_rate, last_pulled_at)
                VALUES (?, 'instagram', ?, 12000, 4.25, now())
                """, lara, UUID.randomUUID().toString());

        // Featured, completed, two redemptions: revenue 150 000, discounts 15 000, cost 50 000 + 15 000.
        long featured = newCampaign(brand, lara, "COMPLETED", 50_000);
        feature(featured, lara);
        long offer = newOffer(featured);
        redeem(offer, brand, lara, featured, branch, 100_000, 10_000);
        redeem(offer, brand, lara, featured, branch, 50_000, 5_000);
        review(featured, 5, "Great reach", 1);

        // Completed but not featured: it is not on the portfolio, but its review counts.
        long other = newCampaign(brand, lara, "COMPLETED", 20_000);
        review(other, 4, "Good", 2);

        CreatorPortfolioResponse portfolio = service.view(lara);

        assertThat(portfolio.creatorId()).isEqualTo(lara);
        assertThat(portfolio.displayName()).isEqualTo("Lara");
        assertThat(portfolio.foodCategory()).isEqualTo("Burgers");
        assertThat(portfolio.location()).isEqualTo("Beirut");
        assertThat(portfolio.priceMinCents()).isEqualTo(1000);
        assertThat(portfolio.priceMaxCents()).isEqualTo(5000);

        assertThat(portfolio.socialAccounts()).hasSize(1);
        assertThat(portfolio.socialAccounts().get(0).platform()).isEqualTo("instagram");
        assertThat(portfolio.socialAccounts().get(0).followers()).isEqualTo(12000);
        assertThat(portfolio.socialAccounts().get(0).engagementRate()).isEqualByComparingTo("4.25");
        assertThat(portfolio.socialAccounts().get(0).lastPulledAt()).isNotNull();

        assertThat(portfolio.featuredCampaigns()).hasSize(1);
        CreatorPortfolioResponse.FeaturedCampaignView result = portfolio.featuredCampaigns().get(0);
        assertThat(result.campaignId()).isEqualTo(featured);
        assertThat(result.verified()).isTrue();
        assertThat(result.redemptions()).isEqualTo(2);
        // (150 000 - 65 000) / 65 000 = 130.77 %
        assertThat(result.roiPercent()).isEqualByComparingTo("130.8");

        assertThat(portfolio.reviewCount()).isEqualTo(2);
        assertThat(portfolio.averageRating()).isEqualByComparingTo("4.5");
        assertThat(portfolio.reviews()).extracting(CreatorPortfolioResponse.ReviewView::text)
                .containsExactly("Great reach", "Good"); // newest first
    }

    // TC-F1.1.2 / EX-F1.1.1
    @Test
    void aCompletedCampaignWithoutRedemptionsIsShownButNotVerified() {
        long lara = newCreator(true);
        long campaign = newCampaign(newBrand(), lara, "COMPLETED", 50_000);
        feature(campaign, lara);

        CreatorPortfolioResponse portfolio = service.view(lara);

        assertThat(portfolio.displayName()).isEqualTo("Lara"); // the rest of the portfolio is still shown
        assertThat(portfolio.featuredCampaigns()).hasSize(1);
        assertThat(portfolio.featuredCampaigns().get(0).verified()).isFalse();
        assertThat(portfolio.featuredCampaigns().get(0).redemptions()).isZero();
        assertThat(portfolio.featuredCampaigns().get(0).roiPercent()).isNull();
    }

    // TC-F1.1.3 / EX-F1.1.2
    @Test
    void aNewCreatorWithNoCampaignOrReviewGetsEmptyListsAndNoRating() {
        long lara = newCreator(true);

        CreatorPortfolioResponse portfolio = service.view(lara);

        assertThat(portfolio.displayName()).isEqualTo("Lara");
        assertThat(portfolio.socialAccounts()).isEmpty();
        assertThat(portfolio.featuredCampaigns()).isEmpty();
        assertThat(portfolio.reviews()).isEmpty();
        assertThat(portfolio.reviewCount()).isZero();
        assertThat(portfolio.averageRating()).isNull();
    }

    @Test
    void aCompletedCampaignThatIsNotFeaturedIsNotShown() {
        long lara = newCreator(true);
        newCampaign(newBrand(), lara, "COMPLETED", 50_000);

        assertThat(service.view(lara).featuredCampaigns()).isEmpty();
    }

    @Test
    void reviewsOfOtherCreatorsAreNotIncluded() {
        long lara = newCreator(true);
        long other = newCreator(true);
        long brand = newBrand();
        review(newCampaign(brand, lara, "COMPLETED", 50_000), 5, "Lara's review", 1);
        review(newCampaign(brand, other, "COMPLETED", 50_000), 1, "Someone else's review", 1);

        CreatorPortfolioResponse portfolio = service.view(lara);

        assertThat(portfolio.reviewCount()).isEqualTo(1);
        assertThat(portfolio.averageRating()).isEqualByComparingTo("5.0");
        assertThat(portfolio.reviews().get(0).text()).isEqualTo("Lara's review");
    }

    @Test
    void anAccountThatNeedsReconnectionStillShowsItsLastFiguresAndTheOldTime() {
        long lara = newCreator(true);
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, status, followers, engagement_rate, last_pulled_at)
                VALUES (?, 'instagram', ?, 'NEEDS_RECONNECTION', 9000, 3.10, now() - interval '2 days')
                """, lara, UUID.randomUUID().toString());

        CreatorPortfolioResponse.AccountView account = service.view(lara).socialAccounts().get(0);

        assertThat(account.followers()).isEqualTo(9000);
        assertThat(account.lastPulledAt()).isBefore(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    // EX-F20.1.3 / TC-F20.1.4: a creator who waits for approval is not visible.
    @Test
    void aCreatorWhoIsNotApprovedOrDoesNotExistIsNotFound() {
        long pending = newCreator(false);

        assertThatThrownBy(() -> service.view(pending)).isInstanceOf(CreatorNotFoundException.class);
        assertThatThrownBy(() -> service.view(-1)).isInstanceOf(CreatorNotFoundException.class);
    }
}
