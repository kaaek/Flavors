package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.UpdateBrandProfileRequest;
import com.flavors.service.BrandProfileService;
import com.flavors.support.BrandFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BrandProfileServiceImplIT {

    @Autowired
    BrandProfileService profile;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void storedProfileReturnsCuisineAndDescription() {
        long id = register("Burgers", "Smash burgers");

        var stored = profile.get(id);

        assertThat(stored.cuisine()).isEqualTo("Burgers");
        assertThat(stored.description()).isEqualTo("Smash burgers");
        assertThat(stored.businessName()).isEqualTo("Karim's Kitchen");
    }

    @Test
    void existingBrandWithoutThemCanAddAndClearThem() {
        long id = register(null, null);
        assertThat(profile.get(id).cuisine()).isNull();

        profile.update(id, new UpdateBrandProfileRequest("Lebanese", "Family recipes"));
        assertThat(profile.get(id).cuisine()).isEqualTo("Lebanese");

        profile.update(id, new UpdateBrandProfileRequest("  ", null));
        assertThat(profile.get(id).cuisine()).isNull();
        assertThat(profile.get(id).description()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'BRAND_PROFILE_UPDATED' AND entity_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    void tooLongCuisineIsRejected() {
        long id = register(null, null);

        assertThatThrownBy(() -> profile.update(id, new UpdateBrandProfileRequest("x".repeat(51), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private long register(String cuisine, String description) {
        return BrandFixtures.brand(jdbc, "Karim's Kitchen", cuisine, description);
    }
}
