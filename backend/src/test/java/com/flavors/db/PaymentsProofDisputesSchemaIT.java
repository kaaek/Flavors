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
 * Checks the constraints in V5__payments_proof_disputes.sql. Each test gets its own brand,
 * creator and campaign, so the tests don't depend on each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentsProofDisputesSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    long brand;
    long admin;
    long campaign;

    @BeforeEach
    void insertCampaign() {
        brand = insertUser("BRAND");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Cafe', 'Karim', '+961')",
                brand);
        long creator = insertUser("CREATOR");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Lara', 'Desserts', 'Beirut', 0, 0)
                """, creator);
        admin = insertUser("ADMIN");
        campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, start_date, last_posting_date)
                VALUES (?, ?, 'Launch', 'ACTIVE', 50000, DATE '2026-11-01', DATE '2026-11-10')
                RETURNING id
                """, Long.class, brand, creator);
    }

    @Test
    void disputeSplitFitsInEscrow() {
        // 60% to the creator after a dispute: share 30000, fee 4500, net 25500, refund 20000.
        assertThat(insertEscrow(4500, 25500, 20000)).isPositive();
    }

    @Test
    void escrowPayoutAboveAmountIsRejected() {
        assertThatThrownBy(() -> insertEscrow(7500, 42500, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameProviderPaymentIsRecordedOnce() {
        String providerPaymentId = unique();
        String insert = "INSERT INTO escrow_payment (campaign_id, amount_cents, provider, provider_payment_id) VALUES (?, 50000, 'test', ?)";
        jdbc.update(insert, campaign, providerPaymentId);

        long otherCampaign = jdbc.queryForObject(
                "INSERT INTO campaign (brand_id, name) VALUES (?, 'Other') RETURNING id", Long.class, brand);
        assertThatThrownBy(() -> jdbc.update(insert, otherCampaign, providerPaymentId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void oneFinalSubmissionPerRoundButDraftsAllowed() {
        long first = insertSubmission(1, "SUBMITTED");

        assertThatThrownBy(() -> insertSubmission(1, "SUBMITTED")).isInstanceOf(DuplicateKeyException.class);
        assertThat(insertSubmission(2, "DRAFT")).isNotEqualTo(first);
        assertThatThrownBy(() -> insertSubmission(2, "DRAFT")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void roundAboveThreeIsRejected() {
        assertThatThrownBy(() -> insertSubmission(4, "DRAFT")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void proofFileMustBeImageUpToFiveMegabytes() {
        long submission = insertSubmission(1, "DRAFT");
        String insert = "INSERT INTO proof_file (submission_id, storage_key, content_type, size_bytes) VALUES (?, ?, ?, ?)";

        assertThat(jdbc.update(insert, submission, unique(), "image/png", 5 * 1024 * 1024)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(insert, submission, unique(), "image/png", 5 * 1024 * 1024 + 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, submission, unique(), "application/pdf", 100))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectionNeedsReason() {
        long submission = insertSubmission(1, "SUBMITTED");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO proof_review (submission_id, decision, decided_by) VALUES (?, 'REJECTED', ?)",
                submission, brand))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void decidedDisputeNeedsShareAdminAndTime() {
        long dispute = jdbc.queryForObject("""
                INSERT INTO dispute (campaign_id, origin, evidence_deadline)
                VALUES (?, 'REJECTION', now() + interval '3 days')
                RETURNING id
                """, Long.class, campaign);

        assertThatThrownBy(() -> jdbc.update("UPDATE dispute SET status = 'DECIDED' WHERE id = ?", dispute))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("""
                UPDATE dispute SET status = 'DECIDED', creator_share_percent = 60, decided_by = ?, decided_at = now()
                WHERE id = ?
                """, admin, dispute)).isEqualTo(1);
    }

    private long insertEscrow(long feeCents, long creatorNetCents, long refundedCents) {
        return jdbc.queryForObject("""
                INSERT INTO escrow_payment (campaign_id, amount_cents, fee_cents, creator_net_cents, refunded_cents, provider)
                VALUES (?, 50000, ?, ?, ?, 'test')
                RETURNING id
                """, Long.class, campaign, feeCents, creatorNetCents, refundedCents);
    }

    private long insertSubmission(int round, String status) {
        return jdbc.queryForObject("""
                INSERT INTO proof_submission (campaign_id, round, status, submitted_at)
                VALUES (?, ?, ?, CASE WHEN ? = 'SUBMITTED' THEN now() END)
                RETURNING id
                """, Long.class, campaign, round, status, status);
    }

    private long insertUser(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, unique() + "@example.com", role);
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }
}
