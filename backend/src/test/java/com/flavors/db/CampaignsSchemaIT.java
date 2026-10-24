package com.flavors.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the constraints in V3__campaigns.sql. Each test gets its own brand and creator, so the
 * tests don't depend on each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CampaignsSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    long brand;
    long creator;

    @BeforeEach
    void insertBrandAndCreator() {
        brand = insertUser("BRAND");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Cafe', 'Karim', '+961')",
                brand);
        creator = insertUser("CREATOR");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Lara', 'Desserts', 'Beirut', 0, 0)
                """, creator);
    }

    @Test
    void draftMayBeIncomplete() {
        long campaign = jdbc.queryForObject(
                "INSERT INTO campaign (brand_id, name) VALUES (?, 'Draft') RETURNING id", Long.class, brand);

        assertThat(jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, campaign))
                .isEqualTo("DRAFT");
    }

    @Test
    void sentCampaignNeedsCreatorPriceAndDates() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaign (brand_id, name, status) VALUES (?, 'Incomplete', 'AWAITING_CREATOR_RESPONSE')
                """, brand))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insertSentCampaign()).isPositive();
    }

    @Test
    void atMostTwoCorrectionRounds() {
        long campaign = insertSentCampaign();

        assertThatThrownBy(() -> jdbc.update("UPDATE campaign SET correction_rounds_used = 3 WHERE id = ?", campaign))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removalNeedsReason() {
        long campaign = insertSentCampaign();

        assertThatThrownBy(() -> jdbc.update("UPDATE campaign SET status = 'REMOVED' WHERE id = ?", campaign))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneCounterOfferPerCampaign() {
        long campaign = insertSentCampaign();
        String insert = "INSERT INTO counter_offer (campaign_id, price_cents, deliverables) VALUES (?, 60000, '[]')";
        jdbc.update(insert, campaign);

        assertThatThrownBy(() -> jdbc.update(insert, campaign)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void onlyOneReviewPerCampaignWithRatingOneToFive() {
        long campaign = insertSentCampaign();
        String insert = "INSERT INTO review (campaign_id, rating) VALUES (?, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, campaign, 6)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, campaign, 5);
        assertThatThrownBy(() -> jdbc.update(insert, campaign, 4)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void profileWarningNeedsExistingCampaign() {
        // This foreign key was left open in V2 and added in V3.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO profile_warning (user_id, campaign_id, reason) VALUES (?, -1, 'Cancelled')", creator))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertSentCampaign() {
        return jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, start_date, last_posting_date)
                VALUES (?, ?, 'Launch', 'AWAITING_CREATOR_RESPONSE', 50000, DATE '2026-11-01', DATE '2026-11-10')
                RETURNING id
                """, Long.class, brand, creator);
    }

    private long insertUser(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com", role);
    }
}
