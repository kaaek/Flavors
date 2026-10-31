package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.Role;
import com.flavors.exception.AlreadyFeaturedException;
import com.flavors.exception.FeatureNotFoundException;
import com.flavors.exception.InvalidPortfolioException;
import com.flavors.exception.PortfolioNotAvailableException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.PortfolioFeatureRepository;
import com.flavors.service.PortfolioService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The Manage Portfolio rules (UC-F1.2), run against the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PortfolioServiceImplIT {

    @Autowired
    PortfolioService service;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository profiles;

    @Autowired
    PortfolioFeatureRepository features;

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

    private long newCampaign(long brand, long creator, String status) {
        return jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                                      start_date, last_posting_date)
                VALUES (?, ?, 'Summer burgers', ?, 50000, 50000, current_date - 10, current_date - 3)
                RETURNING id
                """, Long.class, brand, creator, status);
    }

    private UpdatePortfolioRequest edit(Long min, Long max) {
        return new UpdatePortfolioRequest("I love burgers", "Desserts", "Sidon", min, max);
    }

    // ---- GET ----

    @Test
    void showsProfileAccountsAndCompletedCampaigns() {
        long lara = newCreator(true);
        long brand = newBrand();
        long completed = newCampaign(brand, lara, "COMPLETED");
        newCampaign(brand, lara, "ACTIVE");
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, followers, engagement_rate, last_pulled_at)
                VALUES (?, 'instagram', ?, 12000, 4.25, now())
                """, lara, UUID.randomUUID().toString());

        PortfolioResponse portfolio = service.getMyPortfolio(lara);

        assertThat(portfolio.displayName()).isEqualTo("Lara");
        assertThat(portfolio.priceMinCents()).isEqualTo(1000);
        assertThat(portfolio.socialAccounts()).hasSize(1);
        assertThat(portfolio.socialAccounts().get(0).followers()).isEqualTo(12000);
        // Only the COMPLETED campaign is offered for featuring, and it starts not featured.
        assertThat(portfolio.completedCampaigns()).hasSize(1);
        assertThat(portfolio.completedCampaigns().get(0).id()).isEqualTo(completed);
        assertThat(portfolio.completedCampaigns().get(0).featured()).isFalse();
    }

    // ---- PUT ----

    // TC-F1.2.1, first call: edit the details.
    @Test
    void putReplacesTheDetails() {
        long lara = newCreator(true);

        PortfolioResponse saved = service.updateMyPortfolio(lara, edit(2000L, 8000L));

        assertThat(saved.bio()).isEqualTo("I love burgers");
        assertThat(saved.foodCategory()).isEqualTo("Desserts");
        assertThat(saved.location()).isEqualTo("Sidon");
        assertThat(saved.priceMaxCents()).isEqualTo(8000);
        // And it is really stored, not only returned.
        assertThat(service.getMyPortfolio(lara).bio()).isEqualTo("I love burgers");
    }

    // TC-F1.2.1 as a whole: the edit and the feature are two calls, and the portfolio shows both.
    @Test
    void editAndFeatureTogetherShowOnThePortfolio() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");

        service.updateMyPortfolio(lara, edit(2000L, 8000L));
        service.featureCampaign(lara, completed);

        PortfolioResponse portfolio = service.getMyPortfolio(lara);
        assertThat(portfolio.bio()).isEqualTo("I love burgers");
        assertThat(portfolio.priceMaxCents()).isEqualTo(8000);
        assertThat(portfolio.completedCampaigns().get(0).featured()).isTrue();
    }

    @Test
    void putNeverRemovesFeaturedCampaigns() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
        service.featureCampaign(lara, completed);

        service.updateMyPortfolio(lara, edit(2000L, 8000L));

        assertThat(features.findByCreatorId(lara)).hasSize(1);
        assertThat(service.getMyPortfolio(lara).completedCampaigns().get(0).featured()).isTrue();
    }

    @Test
    void aRefusedPutDoesNotRemoveFeaturedCampaignsEither() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
        service.featureCampaign(lara, completed);

        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(9000L, 8000L)))
                .isInstanceOf(InvalidPortfolioException.class);

        assertThat(features.findByCreatorId(lara)).hasSize(1);
    }

    // TC-F1.2.3
    @Test
    void putRefusesMinimumAboveMaximumAndSavesNothing() {
        long lara = newCreator(true);

        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(9000L, 8000L)))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Minimum price");

        PortfolioResponse unchanged = service.getMyPortfolio(lara);
        assertThat(unchanged.bio()).isNull();
        assertThat(unchanged.priceMinCents()).isEqualTo(1000);
        assertThat(unchanged.priceMaxCents()).isEqualTo(5000);
    }

    @Test
    void putRefusesAMissingPriceRange() {
        long lara = newCreator(true);

        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(null, 8000L)))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Price range");
    }

    @Test
    void putRefusesATooLongBio() {
        long lara = newCreator(true);
        UpdatePortfolioRequest tooLong = new UpdatePortfolioRequest("x".repeat(CreatorProfile.MAX_BIO_LENGTH + 1),
                "Desserts", "Sidon", 2000L, 8000L);

        assertThatThrownBy(() -> service.updateMyPortfolio(lara, tooLong))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Bio");
    }

    // ---- PATCH ----

    @Test
    void patchChangesOnlyTheFieldsSent() {
        long lara = newCreator(true);

        PortfolioResponse saved = service.patchMyPortfolio(lara, Map.of("bio", "New bio"));

        assertThat(saved.bio()).isEqualTo("New bio");
        assertThat(saved.foodCategory()).isEqualTo("Burgers");
        assertThat(saved.location()).isEqualTo("Beirut");
        assertThat(saved.priceMinCents()).isEqualTo(1000);
        assertThat(saved.priceMaxCents()).isEqualTo(5000);
    }

    @Test
    void patchCanChangeThePriceRangeAndKeepsTheFeaturedCampaigns() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
        service.featureCampaign(lara, completed);

        PortfolioResponse saved = service.patchMyPortfolio(lara, Map.of("priceMinCents", 2000, "priceMaxCents", 9000));

        assertThat(saved.priceMinCents()).isEqualTo(2000);
        assertThat(saved.priceMaxCents()).isEqualTo(9000);
        assertThat(saved.completedCampaigns().get(0).featured()).isTrue();
    }

    @Test
    void patchValidatesTheMergedResult() {
        long lara = newCreator(true);

        // The stored maximum is 5000, so a new minimum of 6000 alone is refused.
        assertThatThrownBy(() -> service.patchMyPortfolio(lara, Map.of("priceMinCents", 6000)))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Minimum price");

        assertThat(service.getMyPortfolio(lara).priceMinCents()).isEqualTo(1000);
    }

    @Test
    void patchWithANullBioClearsTheBio() {
        long lara = newCreator(true);
        service.updateMyPortfolio(lara, edit(2000L, 8000L));
        Map<String, Object> clear = new HashMap<>();
        clear.put("bio", null);

        PortfolioResponse saved = service.patchMyPortfolio(lara, clear);

        assertThat(saved.bio()).isNull();
        assertThat(saved.foodCategory()).isEqualTo("Desserts");
    }

    @Test
    void patchWithANullCategoryIsRefused() {
        long lara = newCreator(true);
        Map<String, Object> clear = new HashMap<>();
        clear.put("foodCategory", null);

        assertThatThrownBy(() -> service.patchMyPortfolio(lara, clear))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void patchRefusesUnknownFieldsAnEmptyBodyAndWrongTypes() {
        long lara = newCreator(true);

        assertThatThrownBy(() -> service.patchMyPortfolio(lara, Map.of("bioo", "typo")))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Unknown field: bioo");
        assertThatThrownBy(() -> service.patchMyPortfolio(lara, Map.of()))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("at least one field");
        assertThatThrownBy(() -> service.patchMyPortfolio(lara, Map.of("priceMinCents", "cheap")))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("whole number");
        assertThatThrownBy(() -> service.patchMyPortfolio(lara, Map.of("priceMinCents", 20.5)))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("whole number");
        assertThatThrownBy(() -> service.patchMyPortfolio(lara, Map.of("bio", 42)))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("must be text");
    }

    // ---- POST /featured ----

    @Test
    void featuringACompletedCampaignWorks() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");

        PortfolioResponse.CampaignView view = service.featureCampaign(lara, completed);

        assertThat(view.id()).isEqualTo(completed);
        assertThat(view.featured()).isTrue();
        assertThat(features.findByCreatorId(lara)).hasSize(1);
    }

    // TC-F1.2.2
    @Test
    void featuringACampaignThatIsNotCompletedIsRefused() {
        long lara = newCreator(true);
        long active = newCampaign(newBrand(), lara, "ACTIVE");

        assertThatThrownBy(() -> service.featureCampaign(lara, active))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("completed");

        assertThat(features.findByCreatorId(lara)).isEmpty();
    }

    @Test
    void featuringAnotherCreatorsCampaignOrAnUnknownOneIsRefused() {
        long lara = newCreator(true);
        long other = newCreator(true);
        long othersCompleted = newCampaign(newBrand(), other, "COMPLETED");

        assertThatThrownBy(() -> service.featureCampaign(lara, othersCompleted))
                .isInstanceOf(InvalidPortfolioException.class);
        assertThatThrownBy(() -> service.featureCampaign(lara, -1L))
                .isInstanceOf(InvalidPortfolioException.class);
        assertThatThrownBy(() -> service.featureCampaign(lara, null))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("campaignId");

        assertThat(features.findByCreatorId(other)).isEmpty();
    }

    @Test
    void featuringTheSameCampaignTwiceIsAConflict() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
        service.featureCampaign(lara, completed);

        assertThatThrownBy(() -> service.featureCampaign(lara, completed))
                .isInstanceOf(AlreadyFeaturedException.class);

        assertThat(features.findByCreatorId(lara)).hasSize(1);
    }

    @Test
    void parallelRequestsToFeatureTheSameCampaignLeaveOneRow() throws Exception {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
        int requests = 4;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Throwable>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.featureCampaign(lara, completed);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        start.countDown();

        int successes = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> result : results) {
            Throwable failure = result.get(30, TimeUnit.SECONDS);
            if (failure == null) {
                successes++;
            } else {
                failures.add(failure);
            }
        }
        pool.shutdown();

        // One request wins. The others are refused either by the "already featured" check or, when they
        // raced past it, by the primary key (which the exception handler turns into a 409).
        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(requests - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfAny(AlreadyFeaturedException.class, DataIntegrityViolationException.class));
        assertThat(features.findByCreatorId(lara)).hasSize(1);
    }

    // ---- DELETE /featured/{id} ----

    @Test
    void unfeaturingRemovesOnlyThatCampaign() {
        long lara = newCreator(true);
        long brand = newBrand();
        long first = newCampaign(brand, lara, "COMPLETED");
        long second = newCampaign(brand, lara, "COMPLETED");
        service.featureCampaign(lara, first);
        service.featureCampaign(lara, second);

        service.unfeatureCampaign(lara, first);

        assertThat(features.findByCreatorId(lara)).hasSize(1);
        assertThat(features.findByCreatorId(lara).get(0).getCampaignId()).isEqualTo(second);
    }

    @Test
    void unfeaturingACampaignThatIsNotFeaturedIsNotFound() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");

        assertThatThrownBy(() -> service.unfeatureCampaign(lara, completed))
                .isInstanceOf(FeatureNotFoundException.class);
    }

    @Test
    void aCreatorCannotUnfeatureAnotherCreatorsCampaign() {
        long lara = newCreator(true);
        long other = newCreator(true);
        long othersCompleted = newCampaign(newBrand(), other, "COMPLETED");
        service.featureCampaign(other, othersCompleted);

        assertThatThrownBy(() -> service.unfeatureCampaign(lara, othersCompleted))
                .isInstanceOf(FeatureNotFoundException.class);

        assertThat(features.findByCreatorId(other)).hasSize(1);
    }

    // ---- not approved ----

    @Test
    void aCreatorWhoIsNotApprovedHasNoPortfolio() {
        long pending = newCreator(false);

        assertThatThrownBy(() -> service.getMyPortfolio(pending))
                .isInstanceOf(PortfolioNotAvailableException.class);
        assertThatThrownBy(() -> service.updateMyPortfolio(pending, edit(2000L, 8000L)))
                .isInstanceOf(PortfolioNotAvailableException.class);
        assertThatThrownBy(() -> service.patchMyPortfolio(pending, Map.of("bio", "x")))
                .isInstanceOf(PortfolioNotAvailableException.class);
        assertThatThrownBy(() -> service.featureCampaign(pending, 1L))
                .isInstanceOf(PortfolioNotAvailableException.class);
        assertThatThrownBy(() -> service.unfeatureCampaign(pending, 1L))
                .isInstanceOf(PortfolioNotAvailableException.class);
    }
}