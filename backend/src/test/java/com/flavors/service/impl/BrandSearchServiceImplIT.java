package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.BrandSearchCriteria;
import com.flavors.dto.BrandSearchResult;
import com.flavors.service.BrandSearchService;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real SQL on real Postgres; each test uses its own random word so shared data can't interfere. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BrandSearchServiceImplIT {

    @Autowired
    BrandSearchService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void findsBrandsByCuisine() {
        String token = unique();
        long match = brand("Karim " + token, "Burgers", "Smash", "ACTIVE", "Hamra:Beirut");
        long other = brand("Sushi " + token, "Sushi", null, "ACTIVE", "Hamra:Beirut");

        assertThat(ids(new BrandSearchCriteria(token, "burgers", null, null, null))).containsExactly(match);
        assertThat(ids(new BrandSearchCriteria(token, null, null, null, null))).contains(match, other);
    }

    @Test
    void findsBrandsByBranchLocationAndReturnsAllBranches() {
        String town = unique();
        long match = brand("Karim", "Burgers", null, "ACTIVE", "Hamra:" + town, "Jounieh:Keserwan");
        long other = brand("Elsewhere", "Burgers", null, "ACTIVE", "Mar Mikhael:" + unique());

        var found = service.search(new BrandSearchCriteria(null, null, town, null, null));

        assertThat(found.items()).extracting(BrandSearchResult::brandId).containsExactly(match);
        assertThat(found.items().get(0).branchLocations()).containsExactly(town, "Keserwan");
        assertThat(ids(new BrandSearchCriteria(town, null, null, null, null))).containsExactly(match);
        assertThat(ids(new BrandSearchCriteria(town, null, null, null, null))).doesNotContain(other);
    }

    @Test
    void plainTextMatchesDescriptionAndToleratesTypos() {
        String token = unique();
        long match = brand("Place " + token, "Lebanese", "Famous for manakeesh", "ACTIVE", "Hamra:Beirut");

        assertThat(ids(new BrandSearchCriteria("manakeesh " + token, null, null, null, null))).contains(match);
        assertThat(ids(new BrandSearchCriteria("manakish " + token, null, null, null, null))).contains(match);
    }

    @Test
    void suspendedBrandsAreHidden() {
        String token = unique();
        brand("Gone " + token, "Burgers", null, "SUSPENDED", "Hamra:Beirut");

        assertThat(ids(new BrandSearchCriteria(token, null, null, null, null))).isEmpty();
    }

    @Test
    void brandsWithoutCuisineStillAppear() {
        String token = unique();
        long id = brand("Plain " + token, null, null, "ACTIVE", "Hamra:Beirut");

        assertThat(ids(new BrandSearchCriteria(token, null, null, null, null))).containsExactly(id);
    }

    @Test
    void pagesAndTotals() {
        String token = unique();
        long a = brand("Aa " + token, "Burgers", null, "ACTIVE", "Hamra:Beirut");
        long b = brand("Bb " + token, "Burgers", null, "ACTIVE", "Hamra:Beirut");
        long c = brand("Cc " + token, "Burgers", null, "ACTIVE", "Hamra:Beirut");

        var first = service.search(new BrandSearchCriteria(token, null, null, 0, 2));
        var second = service.search(new BrandSearchCriteria(token, null, null, 1, 2));

        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.items()).extracting(BrandSearchResult::brandId).containsExactly(a, b);
        assertThat(second.items()).extracting(BrandSearchResult::brandId).containsExactly(c);
    }

    @Test
    void resultsNeverCarryContactDetails() {
        assertThat(Arrays.stream(BrandSearchResult.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("phone", "contactPerson", "email")
                .containsExactly("brandId", "businessName", "cuisine", "description", "branchLocations");
    }

    @Test
    void userInputCannotInjectSql() {
        String token = unique();
        long id = brand("Safe " + token, "Burgers", null, "ACTIVE", "Hamra:Beirut");

        assertThat(ids(new BrandSearchCriteria("'; DROP TABLE brand; --", null, null, null, null))).doesNotContain(id);
        assertThat(ids(new BrandSearchCriteria(token, null, null, null, null))).containsExactly(id);
    }

    private List<Long> ids(BrandSearchCriteria criteria) {
        return service.search(criteria).items().stream().map(BrandSearchResult::brandId).toList();
    }

    private static String unique() {
        // Random letters only, so it reads as one word and can't collide with another test's data.
        StringBuilder word = new StringBuilder("zq");
        java.util.concurrent.ThreadLocalRandom.current().ints(8, 'a', 'z' + 1).forEach(c -> word.append((char) c));
        return word.append("xk").toString();
    }

    /** Branches are given as "name:location". */
    private long brand(String name, String cuisine, String description, String status, String... branches) {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'BRAND', ?) RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com", status);
        jdbc.update("""
                INSERT INTO brand (user_id, business_name, contact_person, phone, cuisine, description)
                VALUES (?, ?, 'Secret Person', '+961 1 000000', ?, ?)
                """, id, name, cuisine, description);
        for (String branch : branches) {
            String[] parts = branch.split(":");
            jdbc.update("INSERT INTO branch (brand_id, name, location) VALUES (?, ?, ?)", id, parts[0], parts[1]);
        }
        return id;
    }
}
