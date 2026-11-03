package com.flavors.service.impl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.flavors.TestcontainersConfiguration;
import com.flavors.service.SavedCreatorService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SavedCreatorServiceImplIT {

    @Autowired
    SavedCreatorService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savingTwiceCreatesOnlyOneRow() {
        Long brandId = createBrand();
        Long creatorId = createApprovedCreator();

        service.saveCreator(brandId, creatorId);
        service.saveCreator(brandId, creatorId);

        assertThat(countSaved(brandId, creatorId)).isEqualTo(1);
        assertThat(service.getSavedCreators(brandId))
                .hasSize(1)
                .allSatisfy(saved -> assertThat(saved.getCreatedAt()).isNotNull());
    }

    @Test
    void listsNewestSavedCreatorsFirst() {
        Long brandId = createBrand();
        Long first = createApprovedCreator();
        Long second = createApprovedCreator();

        service.saveCreator(brandId, first);
        service.saveCreator(brandId, second);

        jdbc.update("""
                UPDATE saved_creator
                SET created_at = now() - interval '1 day'
                WHERE brand_id = ? AND creator_id = ?
                """, brandId, first);

        var saved = service.getSavedCreators(brandId);

        assertThat(saved).extracting(item -> item.getCreatorId())
                .containsExactly(second, first);
    }

    @Test
    void removingAnUnsavedCreatorDoesNothing() {
        Long brandId = createBrand();
        Long creatorId = createApprovedCreator();

        service.removeCreator(brandId, creatorId);

        assertThat(countSaved(brandId, creatorId)).isZero();
    }

    @Test
    void foreignKeysRejectUnknownBrandOrCreator() {
        Long brandId = createBrand();
        Long creatorId = createApprovedCreator();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO saved_creator (brand_id, creator_id)
                VALUES (?, ?)
                """, -1L, creatorId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO saved_creator (brand_id, creator_id)
                VALUES (?, ?)
                """, brandId, -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int countSaved(Long brandId, Long creatorId) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM saved_creator
                WHERE brand_id = ? AND creator_id = ?
                """, Integer.class, brandId, creatorId);
    }

    private Long createUser(String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class,
                UUID.randomUUID() + "@example.com",
                "test-password-hash",
                role);
    }

    private Long createBrand() {
        Long id = createUser("BRAND");

        jdbc.update("""
                INSERT INTO brand
                    (user_id, business_name, contact_person, phone)
                VALUES (?, ?, ?, ?)
                """, id, "Test Restaurant", "Test Owner", "12345678");

        return id;
    }

    private Long createApprovedCreator() {
        Long id = createUser("CREATOR");

        jdbc.update("""
                INSERT INTO creator_profile
                    (user_id, display_name, food_category, location,
                     price_min_cents, price_max_cents, verification_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, "Test Creator", "Food", "Beirut",
                1000L, 5000L, "APPROVED");

        return id;
    }
}
