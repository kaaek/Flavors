package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.service.EscrowReleaseService;
import com.flavors.service.ProofReviewService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EscrowReleaseServiceImplIT {

    @Autowired
    EscrowReleaseService releases;

    @Autowired
    ProofReviewService reviews;

    @Autowired
    EscrowReleaseOperationRepository operations;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    void approvalAtomicallyAuthorizesAndRepeatedRequestsDoNotDuplicateOperationOrAudit() {
        Fixture f = fixture();
        var first = approve(f);
        assertThat(approve(f)).isEqualTo(first);
        assertThat(releases.authorizeApproved(f.campaign(), f.submission(), first.id())).isFalse();
        EscrowReleaseOperation operation = operations.findByEscrowId(f.escrow()).orElseThrow();
        assertThat(operation.getReviewId()).isEqualTo(first.id());
        assertThat(operation.getAuthorizationSource()).isEqualTo(ReleaseAuthorizationSource.APPROVAL);
        assertThat(operation.getOriginalAmountCents()).isEqualTo(20000);
        assertThat(operation.getFeeCents()).isEqualTo(3000);
        assertThat(operation.getCreatorNetCents()).isEqualTo(17000);
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.AUTHORIZED);
        assertThat(operation.getProviderRef()).isNull();
        assertThat(operation.getPaymentTransactionId()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ESCROW_RELEASE_AUTHORIZED' "
                + "AND entity_id = ?", Integer.class, operation.getId())).isEqualTo(1);
        assertHeld(f);
    }

    @Test
    void concurrentApprovalRequestsAndDirectAuthorizationsAreIdempotent() throws Exception {
        Fixture current = fixture();
        CountDownLatch approvalStart = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> after(approvalStart, () -> approve(current)));
        var second = CompletableFuture.supplyAsync(() -> after(approvalStart, () -> approve(current)));
        approvalStart.countDown();
        assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        assertThat(countOperations(current)).isEqualTo(1);

        Fixture legacy = fixture();
        long review = legacyReview(legacy, "APPROVED");
        CountDownLatch releaseStart = new CountDownLatch(1);
        var a = CompletableFuture.supplyAsync(() -> after(releaseStart,
                () -> releases.authorizeApproved(legacy.campaign(), legacy.submission(), review)));
        var b = CompletableFuture.supplyAsync(() -> after(releaseStart,
                () -> releases.authorizeApproved(legacy.campaign(), legacy.submission(), review)));
        releaseStart.countDown();
        assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(countOperations(legacy)).isEqualTo(1);
        assertHeld(legacy);
    }

    @Test
    void failureToAuthorizeRollsBackTheNewApprovalAndItsAuditAndDeadlineChange() {
        Fixture f = fixture();
        jdbc.update("UPDATE escrow_payment SET status = 'FROZEN' WHERE id = ?", f.escrow());
        assertThatThrownBy(() -> approve(f)).isInstanceOf(DeliveryProofConflictException.class);
        assertNoApprovalOrOperation(f);
        assertThat(jdbc.queryForObject("SELECT proof_review_deadline IS NOT NULL FROM campaign WHERE id = ?",
                Boolean.class, f.campaign())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PROOF_APPROVED' "
                + "AND details->>'campaignId' = ?", Integer.class, Long.toString(f.campaign()))).isZero();
    }

    @Test
    void rollingBackAfterAuthorizationRollsBackTheReviewOperationAndAuditTogether() {
        Fixture f = fixture();
        tx.executeWithoutResult(s -> {
            approve(f);
            assertThat(countOperations(f)).isEqualTo(1);
            s.setRollbackOnly();
        });
        assertNoApprovalOrOperation(f);
        assertHeld(f);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ESCROW_RELEASE_AUTHORIZED' "
                + "AND details->>'campaignId' = ?", Integer.class, Long.toString(f.campaign()))).isZero();
        approve(f);
        assertThat(countOperations(f)).isEqualTo(1);
    }

    @Test
    void rejectionAndForeignOrStaleApprovalsCannotAuthorizeRelease() {
        Fixture rejected = fixture();
        long rejectedReview = legacyReview(rejected, "REJECTED");
        assertThatThrownBy(() -> releases.authorizeApproved(rejected.campaign(), rejected.submission(), rejectedReview))
                .isInstanceOf(DeliveryProofConflictException.class);
        assertThat(countOperations(rejected)).isZero();
        // Freezing remains outside Step 3; rejection alone is enough to reject authorization.
        assertHeld(rejected);

        Fixture approved = fixture();
        long approvedReview = legacyReview(approved, "APPROVED");
        assertThatThrownBy(() -> releases.authorizeApproved(approved.campaign(), approved.submission(), rejectedReview))
                .isInstanceOf(DeliveryProofConflictException.class);
        jdbc.update("UPDATE campaign SET correction_rounds_used = 1 WHERE id = ?", approved.campaign());
        jdbc.update("INSERT INTO proof_submission (campaign_id, round, status, submitted_at) "
                + "VALUES (?, 2, 'SUBMITTED', now())", approved.campaign());
        assertThatThrownBy(() -> releases.authorizeApproved(approved.campaign(), approved.submission(), approvedReview))
                .isInstanceOf(DeliveryProofConflictException.class);
        assertThat(countOperations(approved)).isZero();
    }

    @Test
    void conflictingOperationPreventsAnotherRelease() {
        Fixture f = fixture();
        long review = legacyReview(f, "APPROVED");
        operations.saveAndFlush(new EscrowReleaseOperation(f.escrow(), f.submission(), null,
                ReleaseAuthorizationSource.TIMER_C, 20000, Instant.now()));
        assertThatThrownBy(() -> releases.authorizeApproved(f.campaign(), f.submission(), review))
                .isInstanceOf(DeliveryProofConflictException.class);
        assertThat(countOperations(f)).isEqualTo(1);
        assertHeld(f);
    }

    @Test
    void recoveryAndAnIdenticalApprovalRetryRepairOldReviewsWithoutResettingAuthorization() {
        Fixture byRecovery = fixture();
        legacyReview(byRecovery, "APPROVED");
        Fixture byRetry = fixture();
        legacyReview(byRetry, "APPROVED");
        approve(byRetry);
        assertThat(countOperations(byRetry)).isEqualTo(1);
        assertThat(releases.recoverApprovedReviews()).isGreaterThanOrEqualTo(1);
        var operation = operations.findByEscrowId(byRecovery.escrow()).orElseThrow();
        Instant authorized = operation.getAuthorizedAt();
        releases.recoverApprovedReviews();
        approve(byRecovery);
        assertThat(operations.findByEscrowId(byRecovery.escrow()).orElseThrow().getAuthorizedAt()).isEqualTo(authorized);
        assertThat(countOperations(byRecovery)).isEqualTo(1);
        assertHeld(byRecovery);
    }

    private com.flavors.dto.ProofReviewResponse approve(Fixture f) {
        return reviews.review(f.brand(), f.campaign(), f.submission(), new ProofReviewRequest(ProofReviewDecision.APPROVED, null));
    }

    private long legacyReview(Fixture f, String decision) {
        long id = jdbc.queryForObject("""
                INSERT INTO proof_review (submission_id, decision, reason, decided_by)
                VALUES (?, ?, CASE WHEN ? = 'REJECTED' THEN 'Missing delivery' END, ?) RETURNING id
                """, Long.class, f.submission(), decision, decision, f.brand());
        jdbc.update("UPDATE campaign SET proof_review_deadline = null WHERE id = ?", f.campaign());
        return id;
    }

    private int countOperations(Fixture f) {
        return jdbc.queryForObject("SELECT count(*) FROM escrow_release_operation WHERE escrow_id = ?", Integer.class, f.escrow());
    }

    private void assertNoApprovalOrOperation(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_review WHERE submission_id = ?", Integer.class,
                f.submission())).isZero();
        assertThat(countOperations(f)).isZero();
    }

    private void assertHeld(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?", String.class, f.escrow()))
                .isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT released_at IS NULL FROM escrow_payment WHERE id = ?", Boolean.class,
                f.escrow())).isTrue();
    }

    private static <T> T after(CountDownLatch latch, java.util.function.Supplier<T> action) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Fixture fixture() {
        long brand = user("BRAND");
        long creator = user("CREATOR");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'K', '1')", brand);
        jdbc.update("INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents) "
                + "VALUES (?, 'Creator', 'Food', 'Beirut', 0, 0)", creator);
        long campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                    start_date, last_posting_date, proof_review_deadline)
                VALUES (?, ?, 'Authorization', 'PROOF_SUBMITTED', 20000, 20000, current_date, current_date,
                    now() + interval '5 days') RETURNING id
                """, Long.class, brand, creator);
        long escrow = jdbc.queryForObject("INSERT INTO escrow_payment (campaign_id, amount_cents, provider, status, held_at) "
                + "VALUES (?, 20000, 'test-collection', 'HELD', now()) RETURNING id", Long.class, campaign);
        long submission = jdbc.queryForObject("INSERT INTO proof_submission (campaign_id, round, status, submitted_at) "
                + "VALUES (?, 1, 'SUBMITTED', now()) RETURNING id", Long.class, campaign);
        return new Fixture(brand, campaign, escrow, submission);
    }

    private long user(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com", role);
    }

    private record Fixture(long brand, long campaign, long escrow, long submission) {
    }
}
