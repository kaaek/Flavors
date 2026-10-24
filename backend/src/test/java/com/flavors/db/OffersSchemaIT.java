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
 * Checks the constraints in V4__offers_redemptions.sql. Each test gets its own brand, creator,
 * branch and campaign, so the tests don't depend on each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OffersSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    long brand;
    long creator;
    long branch;
    long campaign;

    @BeforeEach
    void insertCampaign() {
        brand = insertUser("BRAND");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Cafe', 'Karim', '+961')",
                brand);
        branch = jdbc.queryForObject(
                "INSERT INTO branch (brand_id, name, location) VALUES (?, 'Hamra', 'Beirut') RETURNING id", Long.class, brand);
        creator = insertUser("CREATOR");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Lara', 'Desserts', 'Beirut', 0, 0)
                """, creator);
        campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, start_date, last_posting_date)
                VALUES (?, ?, 'Launch', 'ACTIVE', 50000, DATE '2026-11-01', DATE '2026-11-10')
                RETURNING id
                """, Long.class, brand, creator);
    }

    @Test
    void discountMustBeOneToHundredPercent() {
        assertThatThrownBy(() -> insertOffer(0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOffer(101)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void perPersonLimitDefaultsToOne() {
        long offer = insertOffer(20);

        assertThat(jdbc.queryForObject("SELECT per_person_limit FROM offer WHERE id = ?", Integer.class, offer))
                .isEqualTo(1);
    }

    @Test
    void validHoursMustBothBeSet() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO offer (campaign_id, discount_percent, expires_at, valid_from)
                VALUES (?, 20, now() + interval '30 days', TIME '12:00')
                """, campaign))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneUnusedClaimPerPhone() {
        long offer = insertOffer(20);
        String phoneHash = unique();
        long first = insertClaim(offer, phoneHash);

        assertThatThrownBy(() -> insertClaim(offer, phoneHash)).isInstanceOf(DuplicateKeyException.class);

        // Once the first claim is redeemed, the same phone may claim again (if the per-person limit
        // allows it; the application checks that).
        jdbc.update("UPDATE offer_claim SET status = 'REDEEMED' WHERE id = ?", first);
        assertThat(insertClaim(offer, phoneHash)).isPositive();
    }

    @Test
    void sameClaimRedeemsOnlyOnce() {
        long offer = insertOffer(20);
        long claim = insertClaim(offer, unique());
        insertRedemption(offer, claim, 10000, 2000);

        assertThatThrownBy(() -> insertRedemption(offer, claim, 10000, 2000))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void discountCannotExceedBill() {
        long offer = insertOffer(20);
        long claim = insertClaim(offer, unique());

        assertThatThrownBy(() -> insertRedemption(offer, claim, 1000, 2000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scanFailureNeedsKnownReason() {
        String insert = "INSERT INTO scan_failure (qr_token, branch_id, reason) VALUES ('bad', ?, ?)";

        assertThat(jdbc.update(insert, branch, "INVALID_TOKEN")).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(insert, branch, "SOMETHING_ELSE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertOffer(int discountPercent) {
        return jdbc.queryForObject("""
                INSERT INTO offer (campaign_id, discount_percent, expires_at)
                VALUES (?, ?, now() + interval '30 days')
                RETURNING id
                """, Long.class, campaign, discountPercent);
    }

    private long insertClaim(long offer, String phoneHash) {
        return jdbc.queryForObject("""
                INSERT INTO offer_claim (offer_id, phone_enc, phone_hash, consent_at, qr_token)
                VALUES (?, '\\x00', ?, now(), ?)
                RETURNING id
                """, Long.class, offer, phoneHash, unique());
    }

    private void insertRedemption(long offer, long claim, long billCents, long discountCents) {
        jdbc.update("""
                INSERT INTO redemption (claim_id, offer_id, campaign_id, creator_id, brand_id, branch_id, bill_cents, discount_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, claim, offer, campaign, creator, brand, branch, billCents, discountCents);
    }

    private long insertUser(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, unique() + "@example.com", role);
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }
}
