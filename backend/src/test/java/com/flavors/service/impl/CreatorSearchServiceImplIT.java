package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CreatorSearchCriteria;
import com.flavors.dto.CreatorSearchResult;
import com.flavors.service.CreatorSearchService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the real search SQL on real Postgres (pg_trgm included). All tests share one database, so
 * each test makes its own unique location (a random word) and filters or asserts on its own ids.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreatorSearchServiceImplIT {

    @Autowired
    CreatorSearchService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void burgersInBeirutFindsOnlyThatCreator() { // TC-F2.1.1
        String town = unique();
        long match = creator("Mira " + town, "Burgers", town, 1000, 5000, "APPROVED", null);
        long otherCategory = creator("Sami " + town, "Sushi", town, 1000, 5000, "APPROVED", null);
        long otherTown = creator("Lina " + town, "Burgers", unique(), 1000, 5000, "APPROVED", null);

        List<Long> ids = ids(criteria().category("Burgers").location(town).build());

        assertThat(ids).containsExactly(match);
        assertThat(ids).doesNotContain(otherCategory, otherTown);
    }

    @Test
    void plainTextFindsBurgersCreatorsInTheCity() {
        String town = unique();
        long match = creator("Mira", "Burgers", town, 1000, 5000, "APPROVED", null);
        long sushi = creator("Sami", "Sushi", town, 1000, 5000, "APPROVED", null);

        List<Long> ids = ids(criteria().q("burgers " + town).build());

        assertThat(ids).contains(match).doesNotContain(sushi);
    }

    @Test
    void typosStillMatch() {
        String town = unique();
        long match = creator("Mira", "Burgers", town, 1000, 5000, "APPROVED", null);

        assertThat(ids(criteria().q("burgr " + town).build())).contains(match);
        assertThat(ids(criteria().category("burgrs").location(town).build())).contains(match);
    }

    @Test
    void searchesNameAndBioToo() {
        String token = unique();
        long byName = creator("Chef " + token, "Italian", "Tripoli", 1000, 5000, "APPROVED", null);
        long byBio = creator("Noor", "Italian", "Tripoli", 1000, 5000, "APPROVED", "Loves " + token + " pasta");

        assertThat(ids(criteria().q(token).build())).contains(byName, byBio);
    }

    @Test
    void nameMatchesRankAboveBioMatchesAndWildcardsAreLiteral() {
        String token = unique();
        long inBio = creator("Aaa", "Italian", "Sidon", 1000, 5000, "APPROVED", "likes " + token);
        long inCategory = creator("Zzz", token, "Sidon", 1000, 5000, "APPROVED", null);

        // Category is weighted above bio, so Zzz comes first although "Aaa" sorts first by name.
        assertThat(ids(criteria().q(token).build())).containsExactly(inCategory, inBio);
        // "%" typed by the user must not match everything.
        assertThat(ids(criteria().q("%").build())).doesNotContain(inBio);
    }

    @Test
    void pendingAndRejectedCreatorsNeverAppear() { // TC-F2.1.4
        String town = unique();
        long approved = creator("A", "Burgers", town, 1000, 5000, "APPROVED", null);
        long pending = creator("B", "Burgers", town, 1000, 5000, "PENDING_APPROVAL", null);
        long rejected = creator("C", "Burgers", town, 1000, 5000, "REJECTED", null);

        assertThat(ids(criteria().location(town).verified(false).build()))
                .containsExactly(approved)
                .doesNotContain(pending, rejected);
    }

    @Test
    void suspendedCreatorsAreHidden() {
        String town = unique();
        long suspended = creator("A", "Burgers", town, 1000, 5000, "APPROVED", null);
        jdbc.update("UPDATE app_user SET status = 'SUSPENDED' WHERE id = ?", suspended);

        assertThat(ids(criteria().location(town).build())).isEmpty();
    }

    @Test
    void priceRangeMustOverlapTheBrandsRange() { // TC-F2.1.2, FR-F2.5
        String town = unique();
        long cheap = creator("Cheap", "Burgers", town, 1000, 3000, "APPROVED", null);
        long mid = creator("Mid", "Burgers", town, 2000, 6000, "APPROVED", null);
        long dear = creator("Dear", "Burgers", town, 8000, 9000, "APPROVED", null);

        assertThat(ids(criteria().location(town).minPrice(4000L).maxPrice(7000L).build())).containsExactly(mid);
        // Touching at the edge counts as overlap.
        assertThat(ids(criteria().location(town).minPrice(3000L).maxPrice(3000L).build()))
                .containsExactlyInAnyOrder(cheap, mid);
        assertThat(ids(criteria().location(town).minPrice(9000L).build())).containsExactly(dear);
        assertThat(ids(criteria().location(town).maxPrice(999L).build())).isEmpty();
    }

    @Test
    void socialFiltersUseTheConnectedAccount() {
        String town = unique();
        long big = creator("Big", "Burgers", town, 1000, 5000, "APPROVED", null);
        long small = creator("Small", "Burgers", town, 1000, 5000, "APPROVED", null);
        long needsReconnect = creator("Gone", "Burgers", town, 1000, 5000, "APPROVED", null);
        account(big, "INSTAGRAM", 50_000, "4.50", "CONNECTED");
        account(small, "INSTAGRAM", 800, "1.20", "CONNECTED");
        account(needsReconnect, "INSTAGRAM", 90_000, "9.00", "NEEDS_RECONNECTION");

        assertThat(ids(criteria().location(town).minFollowers(10_000L).build())).containsExactly(big);
        assertThat(ids(criteria().location(town).maxFollowers(1_000L).build())).containsExactly(small);
        assertThat(ids(criteria().location(town).minEngagement(new BigDecimal("2.0")).build())).containsExactly(big);
        assertThat(ids(criteria().location(town).platform("instagram").build()))
                .containsExactlyInAnyOrder(big, small);
        assertThat(ids(criteria().location(town).platform("tiktok").build())).isEmpty();
    }

    @Test
    void creatorsWithoutAnAccountStillAppearWithNullFigures() {
        String town = unique();
        long none = creator("None", "Burgers", town, 1000, 5000, "APPROVED", null);

        List<CreatorSearchResult> items = service.search(brand(), criteria().location(town).build()).items();

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.creatorId()).isEqualTo(none);
            assertThat(item.platform()).isNull();
            assertThat(item.followers()).isNull();
            assertThat(item.engagementRate()).isNull();
            assertThat(item.roi()).isNull();
            assertThat(item.redemptions()).isNull();
        });
    }

    @Test
    void resultShowsTheHighestFollowerConnectedAccount() {
        String town = unique();
        long id = creator("Two", "Burgers", town, 1000, 5000, "APPROVED", null);
        account(id, "INSTAGRAM", 100, "1.00", "CONNECTED");
        account(id, "TIKTOK", 7000, "3.25", "CONNECTED");

        CreatorSearchResult item = service.search(brand(), criteria().location(town).build()).items().get(0);

        assertThat(item.platform()).isEqualTo("TIKTOK");
        assertThat(item.followers()).isEqualTo(7000L);
        assertThat(item.engagementRate()).isEqualByComparingTo("3.25");
    }

    @Test
    void roiAndRedemptionFiltersExcludeEveryone() {
        String town = unique();
        creator("A", "Burgers", town, 1000, 5000, "APPROVED", null);

        assertThat(ids(criteria().location(town).minRoi(BigDecimal.ONE).build())).isEmpty();
        assertThat(ids(criteria().location(town).minRedemptions(1L).build())).isEmpty();
    }

    @Test
    void pagesAndTotals() {
        String town = unique();
        long a = creator("Aa", "Burgers", town, 1000, 5000, "APPROVED", null);
        long b = creator("Bb", "Burgers", town, 1000, 5000, "APPROVED", null);
        long c = creator("Cc", "Burgers", town, 1000, 5000, "APPROVED", null);

        var first = service.search(brand(), criteria().location(town).size(2).page(0).build());
        var second = service.search(brand(), criteria().location(town).size(2).page(1).build());

        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.items()).extracting(CreatorSearchResult::creatorId).containsExactly(a, b);
        assertThat(second.items()).extracting(CreatorSearchResult::creatorId).containsExactly(c);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
    }

    @Test
    void savedFlagIsPerBrand() {
        String town = unique();
        long id = creator("A", "Burgers", town, 1000, 5000, "APPROVED", null);
        long me = brand();
        long other = brand();
        jdbc.update("INSERT INTO saved_creator (brand_id, creator_id) VALUES (?, ?)", me, id);

        assertThat(service.search(me, criteria().location(town).build()).items().get(0).saved()).isTrue();
        assertThat(service.search(other, criteria().location(town).build()).items().get(0).saved()).isFalse();
    }

    @Test
    void facetsListOnlyApprovedCreatorsValues() {
        String town = unique();
        String category = unique();
        creator("A", category, town, 1000, 5000, "APPROVED", null);
        String hiddenTown = unique();
        creator("B", "Burgers", hiddenTown, 1000, 5000, "PENDING_APPROVAL", null);

        var facets = service.facets();

        assertThat(facets.locations()).contains(town).doesNotContain(hiddenTown);
        assertThat(facets.categories()).contains(category);
    }

    @Test
    void userInputCannotInjectSql() {
        String town = unique();
        long id = creator("A", "Burgers", town, 1000, 5000, "APPROVED", null);

        assertThat(ids(criteria().q("'; DROP TABLE creator_profile; --").build())).doesNotContain(id);
        assertThat(ids(criteria().location(town).build())).containsExactly(id);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private List<Long> ids(CreatorSearchCriteria criteria) {
        return service.search(brand(), criteria).items().stream().map(CreatorSearchResult::creatorId).toList();
    }

    private static String unique() {
        // Random letters only, so it reads as one word and can't collide with another test's data.
        StringBuilder word = new StringBuilder("zq");
        java.util.concurrent.ThreadLocalRandom.current().ints(8, 'a', 'z' + 1).forEach(c -> word.append((char) c));
        return word.append("xk").toString();
    }

    private long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role) VALUES (?, 'hash', ?) RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com", role);
    }

    private long brand() {
        long id = user("BRAND");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'B', 'P', '1')", id);
        return id;
    }

    private long creator(String name, String category, String location, long min, long max,
                         String status, String bio) {
        long id = user("CREATOR");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, bio, food_category, location,
                    price_min_cents, price_max_cents, verification_status, rejection_reason, storefront_slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, name, bio, category, location, min, max, status,
                status.equals("REJECTED") ? "no" : null, status.equals("APPROVED") ? "s-" + id : null);
        return id;
    }

    private void account(long creatorId, String platform, long followers, String engagement, String status) {
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, status, followers, engagement_rate)
                VALUES (?, ?, ?, ?, ?, ?::numeric)
                """, creatorId, platform, UUID.randomUUID().toString(), status, followers, engagement);
    }

    private static Builder criteria() {
        return new Builder();
    }

    /** Test-side builder so each test names only the filters it uses. */
    private static final class Builder {
        String q, location, category, platform;
        Long minFollowers, maxFollowers, minPrice, maxPrice, minRedemptions;
        BigDecimal minEngagement, minRoi;
        Boolean verified;
        Integer page, size;

        Builder q(String v) { q = v; return this; }
        Builder location(String v) { location = v; return this; }
        Builder category(String v) { category = v; return this; }
        Builder platform(String v) { platform = v; return this; }
        Builder minFollowers(Long v) { minFollowers = v; return this; }
        Builder maxFollowers(Long v) { maxFollowers = v; return this; }
        Builder minPrice(Long v) { minPrice = v; return this; }
        Builder maxPrice(Long v) { maxPrice = v; return this; }
        Builder minEngagement(BigDecimal v) { minEngagement = v; return this; }
        Builder minRoi(BigDecimal v) { minRoi = v; return this; }
        Builder minRedemptions(Long v) { minRedemptions = v; return this; }
        Builder verified(Boolean v) { verified = v; return this; }
        Builder page(Integer v) { page = v; return this; }
        Builder size(Integer v) { size = v; return this; }

        CreatorSearchCriteria build() {
            return new CreatorSearchCriteria(q, location, category, platform, minFollowers, maxFollowers,
                    minEngagement, minPrice, maxPrice, minRoi, minRedemptions, verified, page, size);
        }
    }
}
