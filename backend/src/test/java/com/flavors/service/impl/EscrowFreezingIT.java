package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.repository.EscrowPaymentRepository;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.EscrowReleaseService;
import com.flavors.service.ProofReviewService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "flavors.escrow.release-deadline-cron=-")
class EscrowFreezingIT {
    @Autowired ProofReviewService reviews;
    @Autowired DeliveryProofService proofs;
    @Autowired EscrowReleaseService releases;
    @Autowired EscrowReleaseOperationRepository operations;
    @Autowired EscrowPaymentRepository escrows;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    @Test
    void rejectionCommitsFreezeAndBothAuditsAndRemainsVisibleWithoutADispute() {
        Fixture f = fixture();
        var rejected = reject(f);
        assertThat(reject(f)).isEqualTo(rejected);
        Boolean frozenAgain = tx.execute(s -> releases.freezeForRejection(f.campaign(), f.submission(), rejected.id()));
        assertThat(frozenAgain).isFalse();
        assertFrozen(f);
        assertThat(audits(f, "ESCROW_FROZEN")).isEqualTo(1);
        assertThat(audits(f, "PROOF_REJECTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT proof_review_deadline IS NULL FROM campaign WHERE id = ?",
                Boolean.class, f.campaign())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_review WHERE submission_id = ?",
                Integer.class, f.submission())).isEqualTo(1);
        for (var viewer : List.of(Role.BRAND, Role.CREATOR)) {
            var history = reviews.history(viewer == Role.BRAND ? f.brand() : f.creator(), viewer, f.campaign());
            assertThat(history.reviews()).singleElement().satisfies(r -> {
                assertThat(r.id()).isEqualTo(rejected.id());
                assertThat(r.decision()).isEqualTo(ProofReviewDecision.REJECTED);
                assertThat(r.reason()).isEqualTo("Missing delivery");
            });
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispute WHERE campaign_id = ?",
                Integer.class, f.campaign())).isZero();
        assertThat(releases.authorizeOverdue(f.campaign())).isFalse();
        assertThatThrownBy(() -> releases.authorizeApproved(f.campaign(), f.submission(), rejected.id()))
                .isInstanceOf(DeliveryProofConflictException.class);
    }

    @Test
    void callerRollbackRestoresReviewEscrowDeadlineAndAuditsTogether() {
        Fixture f = fixture();
        tx.executeWithoutResult(s -> {
            reject(f);
            escrows.flush(); // JDBC reads the database, so flush JPA's pending update before asserting.
            assertThat(status(f)).isEqualTo("FROZEN");
            s.setRollbackOnly();
        });
        assertUnchanged(f);
        reject(f);
        assertFrozen(f);
    }

    @ParameterizedTest
    @EnumSource(value = Failure.class)
    void databaseFailureDuringFreezeOrAuditRollsBackTheWholeRejection(Failure failure) {
        Fixture f = fixture();
        String table = failure == Failure.AUDIT ? "audit_log" : "escrow_payment";
        String predicate = failure == Failure.AUDIT
                ? "NEW.action = 'ESCROW_FROZEN' AND NEW.details->>'campaignId' = '" + f.campaign() + "'"
                : "NEW.id = " + f.escrow() + " AND NEW.status = 'FROZEN'";
        jdbc.execute("CREATE FUNCTION step5_fail_freeze() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "
                + predicate + " THEN RAISE EXCEPTION 'test interruption'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER step5_fail_freeze BEFORE " + (failure == Failure.AUDIT ? "INSERT" : "UPDATE")
                + " ON " + table + " FOR EACH ROW EXECUTE FUNCTION step5_fail_freeze()");
        try {
            assertThatThrownBy(() -> reject(f)).isInstanceOf(RuntimeException.class);
            assertUnchanged(f);
        } finally {
            jdbc.execute("DROP TRIGGER step5_fail_freeze ON " + table);
            jdbc.execute("DROP FUNCTION step5_fail_freeze()");
        }
        reject(f);
        assertFrozen(f);
    }

    @Test
    void concurrentIdenticalRejectionsFreezeAndAuditExactlyOnce() throws Exception {
        Fixture f = fixture();
        CountDownLatch start = new CountDownLatch(1);
        var a = async(start, () -> reject(f));
        var b = async(start, () -> reject(f));
        start.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
        assertFrozen(f);
        assertThat(audits(f, "ESCROW_FROZEN")).isEqualTo(1);
        assertThat(audits(f, "PROOF_REJECTED")).isEqualTo(1);
    }

    @Test
    void committedRejectionBeatsAWaitingTimerCandidate() throws Exception {
        Fixture f = fixture();
        CountDownLatch rejected = new CountDownLatch(1);
        CountDownLatch timerStarted = new CountDownLatch(1);
        var rejection = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            reject(f);
            rejected.countDown();
            await(timerStarted);
        }));
        await(rejected);
        var timer = CompletableFuture.supplyAsync(() -> {
            timerStarted.countDown();
            return releases.authorizeOverdue(f.campaign());
        });
        rejection.get(20, TimeUnit.SECONDS);
        assertThat(timer.get(20, TimeUnit.SECONDS)).isFalse();
        assertFrozen(f);
        // Even stale data/candidate selection cannot override the persisted rejection or frozen escrow.
        jdbc.update("UPDATE campaign SET proof_review_deadline = now() - interval '1 second' WHERE id = ?", f.campaign());
        assertThat(releases.authorizeOverdue(f.campaign())).isFalse();
        assertThat(reviews.findOverdueCampaignIds()).doesNotContain(f.campaign());
        assertThat(operations.findByEscrowId(f.escrow())).isEmpty();
    }

    @Test
    void expirationRacingWithRejectionAuthorizesWithoutAcceptingALateRejection() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE campaign SET proof_review_deadline = now() - interval '1 second' WHERE id = ?", f.campaign());
        CountDownLatch start = new CountDownLatch(1);
        var timer = async(start, () -> releases.authorizeOverdue(f.campaign()));
        var rejection = async(start, () -> {
            try { reject(f); return true; }
            catch (DeliveryProofConflictException expected) { return false; }
        });
        start.countDown();
        assertThat(timer.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(rejection.get(20, TimeUnit.SECONDS)).isFalse();
        assertThat(status(f)).isEqualTo("HELD");
        assertThat(operationCount(f)).isEqualTo(1);
        assertThat(audits(f, "ESCROW_FROZEN")).isZero();
        assertThat(audits(f, "PROOF_REJECTED")).isZero();
    }

    @Test
    void anotherBrandCannotRejectOrFreezeTheOwnersEscrow() {
        Fixture f = fixture();
        long foreignBrand = user("BRAND");
        assertThatThrownBy(() -> reviews.review(foreignBrand, f.campaign(), f.submission(),
                new ProofReviewRequest(ProofReviewDecision.REJECTED, "Missing delivery")))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertUnchanged(f);
    }

    @ParameterizedTest
    @EnumSource(value = ProofReviewDecision.class, names = {"APPROVED", "CORRECTIONS_REQUESTED"})
    void approvalAndCorrectionsKeepEscrowHeld(ProofReviewDecision decision) {
        Fixture f = fixture();
        reviews.review(f.brand(), f.campaign(), f.submission(), new ProofReviewRequest(decision, null));
        assertThat(status(f)).isEqualTo("HELD");
        assertThat(audits(f, "ESCROW_FROZEN")).isZero();
    }

    @Test
    void existingReleaseOperationPreventsAConflictingRejection() {
        Fixture f = fixture();
        operations.saveAndFlush(new EscrowReleaseOperation(f.escrow(), f.submission(), null,
                ReleaseAuthorizationSource.TIMER_C, 20000, Instant.now()));
        assertThatThrownBy(() -> reject(f)).isInstanceOf(DeliveryProofConflictException.class);
        assertThat(status(f)).isEqualTo("HELD");
        assertThat(audits(f, "PROOF_REJECTED")).isZero();
        assertThat(audits(f, "ESCROW_FROZEN")).isZero();
        assertThat(operationCount(f)).isEqualTo(1);
    }

    @Test
    void identicalRetryRepairsALegacyRejectionAndPreservesItsHistory() {
        Fixture f = fixture();
        long reviewId = jdbc.queryForObject("INSERT INTO proof_review (submission_id, decision, reason, decided_by) "
                + "VALUES (?, 'REJECTED', 'Missing delivery', ?) RETURNING id", Long.class, f.submission(), f.brand());
        jdbc.update("UPDATE campaign SET proof_review_deadline = null WHERE id = ?", f.campaign());
        assertThat(reject(f).id()).isEqualTo(reviewId);
        assertThat(reject(f).id()).isEqualTo(reviewId);
        assertFrozen(f);
        assertThat(audits(f, "ESCROW_FROZEN")).isEqualTo(1);
        assertThat(audits(f, "PROOF_REJECTED")).isZero(); // Repair does not invent the old review's audit.
    }

    @Test
    void freezeCannotRunOutsideTheBusinessTransaction() {
        Fixture f = fixture();
        assertThatThrownBy(() -> releases.freezeForRejection(f.campaign(), f.submission(), 1L))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertUnchanged(f);
    }

    private com.flavors.dto.ProofReviewResponse reject(Fixture f) {
        return reviews.review(f.brand(), f.campaign(), f.submission(),
                new ProofReviewRequest(ProofReviewDecision.REJECTED, "Missing delivery"));
    }

    private void assertFrozen(Fixture f) {
        assertThat(status(f)).isEqualTo("FROZEN");
        assertThat(operationCount(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, f.campaign()))
                .isEqualTo("PROOF_SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT amount_cents = 20000 AND fee_cents = 0 AND creator_net_cents = 0 "
                + "AND refunded_cents = 0 AND released_at IS NULL FROM escrow_payment WHERE id = ?",
                Boolean.class, f.escrow())).isTrue();
    }

    private void assertUnchanged(Fixture f) {
        assertThat(status(f)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_review WHERE submission_id = ?",
                Integer.class, f.submission())).isZero();
        assertThat(jdbc.queryForObject("SELECT proof_review_deadline IS NOT NULL FROM campaign WHERE id = ?",
                Boolean.class, f.campaign())).isTrue();
        assertThat(audits(f, "PROOF_REJECTED")).isZero();
        assertThat(audits(f, "ESCROW_FROZEN")).isZero();
        assertThat(operationCount(f)).isZero();
    }

    private int audits(Fixture f, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ? AND details->>'campaignId' = ?",
                Integer.class, action, Long.toString(f.campaign()));
    }

    private String status(Fixture f) {
        return jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?", String.class, f.escrow());
    }

    private int operationCount(Fixture f) {
        return jdbc.queryForObject("SELECT count(*) FROM escrow_release_operation WHERE escrow_id = ?",
                Integer.class, f.escrow());
    }

    private static <T> CompletableFuture<T> async(CountDownLatch start, Supplier<T> action) {
        return CompletableFuture.supplyAsync(() -> { await(start); return action.get(); });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out");
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
                    start_date, last_posting_date)
                VALUES (?, ?, 'Freezing', 'ACTIVE', 20000, 20000, current_date - 2, current_date + 2) RETURNING id
                """, Long.class, brand, creator);
        long escrow = jdbc.queryForObject("INSERT INTO escrow_payment (campaign_id, amount_cents, provider, status, held_at) "
                + "VALUES (?, 20000, 'test-only', 'HELD', now()) RETURNING id", Long.class, campaign);
        var proof = proofs.submit(creator, campaign,
                new ProofDraftRequest(List.of("https://example.com/post"), null), List.of());
        return new Fixture(brand, creator, campaign, escrow, proof.submissions().getFirst().id());
    }

    private long user(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com", role);
    }

    enum Failure { AUDIT, ESCROW_UPDATE }
    private record Fixture(long brand, long creator, long campaign, long escrow, long submission) {}
}
