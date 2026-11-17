package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.EscrowReleaseProcessingService;
import com.flavors.service.EscrowReleaseService;
import com.flavors.service.ProofReviewService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "flavors.escrow.release-deadline-cron=-")
class EscrowReleaseTimerCIT {
    @Autowired EscrowReleaseService releases;
    @Autowired EscrowReleaseProcessingService processing;
    @Autowired EscrowReleaseDeadlineJob job;
    @Autowired ProofReviewService reviews;
    @Autowired DeliveryProofService proofs;
    @Autowired EscrowReleaseOperationRepository operations;
    @Autowired CampaignRepository campaigns;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    private final List<Long> fixtureIds = new ArrayList<>();

    @AfterEach
    void stopFixtureTimers() {
        for (long id : fixtureIds) {
            jdbc.update("UPDATE campaign SET proof_review_deadline = null WHERE id = ?", id);
        }
    }

    @Test
    void fiveDayUnansweredProofAuthorizesOnceAndKeepsFundsHeld() {
        Fixture f = fixture();
        Instant submitted = jdbc.queryForObject("SELECT submitted_at FROM proof_submission WHERE id = ?",
                Timestamp.class, f.submission()).toInstant();
        Instant deadline = deadline(f);
        assertThat(Duration.between(submitted, deadline)).isEqualTo(Duration.ofDays(5));
        assertThat(releases.authorizeOverdue(f.campaign())).isFalse();
        assertThat(reviews.findOverdueCampaignIds()).doesNotContain(f.campaign());
        expire(f);
        assertThat(reviews.findOverdueCampaignIds()).contains(f.campaign());
        job.authorizeOverdueReleases();
        EscrowReleaseOperation operation = operation(f);
        assertThat(operation.getAuthorizationSource()).isEqualTo(ReleaseAuthorizationSource.TIMER_C);
        assertThat(operation.getReviewId()).isNull();
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.AUTHORIZED);
        assertThat(operation.getFeeCents()).isEqualTo(3000);
        assertThat(operation.getCreatorNetCents()).isEqualTo(17000);
        assertThat(operation.getProviderRef()).isNull();
        assertThat(operation.getPaymentTransactionId()).isNull();
        job.authorizeOverdueReleases();
        assertThat(releases.authorizeOverdue(f.campaign())).isFalse();
        assertThat(operation(f).getAuthorizedAt()).isEqualTo(operation.getAuthorizedAt());
        assertThat(count(f)).isEqualTo(1);
        assertThat(deadline(f)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_review WHERE submission_id = ?",
                Integer.class, f.submission())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ESCROW_RELEASE_AUTHORIZED' "
                + "AND entity_id = ? AND details->>'source' = 'TIMER_C' AND actor_label = 'system'",
                Integer.class, operation.getId())).isEqualTo(1);
        assertHeld(f);
    }

    @Test
    void recordedApprovalsAndRejectionsWinEvenWithStaleExpiredDeadlines() {
        for (ProofReviewDecision decision : List.of(ProofReviewDecision.APPROVED, ProofReviewDecision.REJECTED)) {
            Fixture f = fixture();
            review(f, decision);
            expire(f); // Simulates a stale candidate or old data with an uncleared deadline.
            assertThat(reviews.findOverdueCampaignIds()).doesNotContain(f.campaign());
            assertThat(releases.authorizeOverdue(f.campaign())).isFalse();
            if (decision == ProofReviewDecision.APPROVED) {
                assertThat(operation(f).getAuthorizationSource()).isEqualTo(ReleaseAuthorizationSource.APPROVAL);
            } else {
                assertThat(count(f)).isZero();
                assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?",
                        String.class, f.escrow())).isEqualTo("FROZEN");
            }
            if (decision == ProofReviewDecision.APPROVED) assertHeld(f);
        }
    }

    @Test
    void correctionCancelsOldTimerAndResubmissionGetsAFreshFiveDays() {
        Fixture first = fixture();
        review(first, ProofReviewDecision.CORRECTIONS_REQUESTED);
        expire(first);
        assertThat(releases.authorizeOverdue(first.campaign())).isFalse();
        assertThat(reviews.findOverdueCampaignIds()).doesNotContain(first.campaign());
        var response = proofs.submit(first.creator(), first.campaign(),
                new ProofDraftRequest(List.of("https://example.com/corrected"), null), List.of());
        Fixture second = new Fixture(first.brand(), first.creator(), first.campaign(), first.escrow(),
                response.submissions().getLast().id());
        Instant submitted = jdbc.queryForObject("SELECT submitted_at FROM proof_submission WHERE id = ?",
                Timestamp.class, second.submission()).toInstant();
        assertThat(Duration.between(submitted, deadline(second))).isEqualTo(Duration.ofDays(5));
        assertThat(releases.authorizeOverdue(second.campaign())).isFalse();
        expire(second);
        assertThat(releases.authorizeOverdue(second.campaign())).isTrue();
        assertThat(operation(second).getSubmissionId()).isEqualTo(second.submission());
        assertHeld(second);
    }

    @Test
    void ineligibleEscrowsAndCampaignsAreExcludedWithoutBlockingEligibleWork() {
        for (String status : List.of("PENDING", "FROZEN", "RELEASED", "REFUNDED")) {
            Fixture f = fixture();
            expire(f);
            // V5 requires released/refunded money to balance.
            jdbc.update("UPDATE escrow_payment SET status = ?, creator_net_cents = ?, refunded_cents = ? WHERE id = ?",
                    status, status.equals("RELEASED") ? 20000 : 0, status.equals("REFUNDED") ? 20000 : 0, f.escrow());
            assertThat(releases.authorizeOverdue(f.campaign())).isFalse();
            assertThat(reviews.findOverdueCampaignIds()).doesNotContain(f.campaign());
        }
        Fixture ended = fixture();
        expire(ended);
        jdbc.update("UPDATE campaign SET status = 'COMPLETED' WHERE id = ?", ended.campaign());
        assertThat(releases.authorizeOverdue(ended.campaign())).isFalse();
        Fixture eligible = fixture();
        expire(eligible);
        assertThat(processing.authorizeOverdueBatch()).isEqualTo(1);
        assertHeld(eligible);
    }

    @Test
    void concurrentSchedulerRunsCommitOneOperationAndOneAudit() throws Exception {
        Fixture f = fixture();
        expire(f);
        CountDownLatch start = new CountDownLatch(1);
        var a = async(start, processing::authorizeOverdueBatch);
        var b = async(start, processing::authorizeOverdueBatch);
        start.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(count(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ESCROW_RELEASE_AUTHORIZED' "
                + "AND entity_id = ?", Integer.class, operation(f).getId())).isEqualTo(1);
        assertHeld(f);
    }

    @Test
    void approvalCommittedFirstPreventsAWaitingDeadlineAuthorization() throws Exception {
        Fixture f = fixture();
        CountDownLatch approved = new CountDownLatch(1);
        CountDownLatch timerStarted = new CountDownLatch(1);
        var approval = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            review(f, ProofReviewDecision.APPROVED);
            approved.countDown();
            await(timerStarted);
        }));
        await(approved);
        var timer = CompletableFuture.supplyAsync(() -> {
            timerStarted.countDown();
            return releases.authorizeOverdue(f.campaign());
        });
        approval.get(20, TimeUnit.SECONDS);
        assertThat(timer.get(20, TimeUnit.SECONDS)).isFalse();
        assertThat(operation(f).getAuthorizationSource()).isEqualTo(ReleaseAuthorizationSource.APPROVAL);
        assertThat(count(f)).isEqualTo(1);
    }

    @Test
    void overdueAuthorizationRacingWithApprovalNeverProducesTwoOperations() throws Exception {
        Fixture f = fixture();
        expire(f);
        CountDownLatch start = new CountDownLatch(1);
        var timer = async(start, () -> releases.authorizeOverdue(f.campaign()));
        var approval = async(start, () -> {
            try {
                review(f, ProofReviewDecision.APPROVED);
                return true;
            } catch (DeliveryProofConflictException expected) {
                return false;
            }
        });
        start.countDown();
        assertThat(timer.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(approval.get(20, TimeUnit.SECONDS)).isFalse();
        assertThat(count(f)).isEqualTo(1);
        assertThat(operation(f).getAuthorizationSource()).isEqualTo(ReleaseAuthorizationSource.TIMER_C);
        assertHeld(f);
    }

    @Test
    void auditFailureRollsBackOnlyOneCampaignAndLaterRunRecoversIt() {
        Fixture failed = fixture();
        Fixture healthy = fixture();
        expire(failed);
        expire(healthy);
        jdbc.execute("CREATE FUNCTION step4_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.action = 'ESCROW_RELEASE_AUTHORIZED' AND NEW.details->>'campaignId' = '"
                + failed.campaign() + "' THEN RAISE EXCEPTION 'test interruption'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER step4_fail_audit BEFORE INSERT ON audit_log "
                + "FOR EACH ROW EXECUTE FUNCTION step4_fail_audit()");
        try {
            assertThat(processing.authorizeOverdueBatch()).isEqualTo(1);
            assertThat(count(failed)).isZero();
            assertThat(deadline(failed)).isNotNull();
            assertThat(reviews.findOverdueCampaignIds()).contains(failed.campaign()).doesNotContain(healthy.campaign());
            assertThat(count(healthy)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ESCROW_RELEASE_AUTHORIZED' "
                    + "AND details->>'campaignId' = ?", Integer.class, Long.toString(failed.campaign()))).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER step4_fail_audit ON audit_log");
            jdbc.execute("DROP FUNCTION step4_fail_audit()");
        }
        assertThat(processing.authorizeOverdueBatch()).isEqualTo(1);
        assertThat(count(failed)).isEqualTo(1);
        assertThat(processing.authorizeOverdueBatch()).isZero();
        assertHeld(failed);
        assertHeld(healthy);
    }

    @Test
    void batchesAreBoundedAndCommittedWorkDoesNotStarveTheNextBatch() {
        List<Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            Fixture f = fixture();
            expire(f);
            fixtures.add(f);
        }
        assertThat(reviews.findOverdueCampaignIds()).hasSize(100);
        assertThat(processing.authorizeOverdueBatch()).isEqualTo(100);
        assertThat(processing.authorizeOverdueBatch()).isEqualTo(1);
        assertThat(processing.authorizeOverdueBatch()).isZero();
        for (Fixture f : fixtures) {
            assertThat(count(f)).isEqualTo(1);
        }
    }

    private void review(Fixture f, ProofReviewDecision decision) {
        reviews.review(f.brand(), f.campaign(), f.submission(), new ProofReviewRequest(decision,
                decision == ProofReviewDecision.REJECTED ? "Missing proof" : null));
    }

    private void expire(Fixture f) {
        jdbc.update("UPDATE campaign SET proof_review_deadline = now() - interval '1 second' WHERE id = ?", f.campaign());
    }

    private Instant deadline(Fixture f) {
        Timestamp result = jdbc.queryForObject("SELECT proof_review_deadline FROM campaign WHERE id = ?",
                Timestamp.class, f.campaign());
        return result == null ? null : result.toInstant();
    }

    private EscrowReleaseOperation operation(Fixture f) {
        return operations.findByEscrowId(f.escrow()).orElseThrow();
    }

    private int count(Fixture f) {
        return jdbc.queryForObject("SELECT count(*) FROM escrow_release_operation WHERE escrow_id = ?",
                Integer.class, f.escrow());
    }

    private void assertHeld(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?", String.class, f.escrow()))
                .isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT released_at IS NULL FROM escrow_payment WHERE id = ?", Boolean.class,
                f.escrow())).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, f.campaign()))
                .isEqualTo("PROOF_SUBMITTED");
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
                VALUES (?, ?, 'Timer C', 'ACTIVE', 20000, 20000, current_date - 2, current_date + 2) RETURNING id
                """, Long.class, brand, creator);
        fixtureIds.add(campaign);
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

    private record Fixture(long brand, long creator, long campaign, long escrow, long submission) {}
}
