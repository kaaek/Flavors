package com.flavors.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the constraints in V2__accounts.sql. Each test creates its own users, so the tests
 * don't depend on each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountsSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void newCreatorStartsPendingApproval() {
        long creator = insertCreator(1000, 5000);

        assertThat(jdbc.queryForObject(
                "SELECT verification_status FROM creator_profile WHERE user_id = ?", String.class, creator))
                .isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void priceMinAboveMaxIsRejected() {
        assertThatThrownBy(() -> insertCreator(5000, 1000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectionNeedsReason() {
        long creator = insertCreator(1000, 5000);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE creator_profile SET verification_status = 'REJECTED' WHERE user_id = ?", creator))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("""
                UPDATE creator_profile SET verification_status = 'REJECTED', rejection_reason = 'Fake followers'
                WHERE user_id = ?
                """, creator)).isEqualTo(1);
    }

    @Test
    void storefrontSlugIsUnique() {
        String slug = unique();
        jdbc.update("UPDATE creator_profile SET storefront_slug = ? WHERE user_id = ?", slug, insertCreator(0, 0));

        long other = insertCreator(0, 0);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE creator_profile SET storefront_slug = ? WHERE user_id = ?", slug, other))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void socialAccountCannotBeConnectedByTwoCreators() {
        String externalId = unique();
        String insert = "INSERT INTO social_account (creator_id, platform, external_id) VALUES (?, 'INSTAGRAM', ?)";
        jdbc.update(insert, insertCreator(0, 0), externalId);

        long other = insertCreator(0, 0);
        assertThatThrownBy(() -> jdbc.update(insert, other, externalId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void branchNeedsExistingBrand() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO branch (brand_id, name, location) VALUES (-1, 'Hamra', 'Beirut')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertCreator(long priceMinCents, long priceMaxCents) {
        long userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'CREATOR') RETURNING id
                """, Long.class, unique() + "@example.com");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Lara', 'Desserts', 'Beirut', ?, ?)
                """, userId, priceMinCents, priceMaxCents);
        return userId;
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }
}
