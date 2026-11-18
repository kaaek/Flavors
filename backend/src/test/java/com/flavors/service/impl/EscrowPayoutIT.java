package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.service.EscrowPayoutService;
import com.flavors.service.ProofReviewService;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, EscrowPayoutIT.Providers.class})
@SpringBootTest(properties = {"flavors.payout.mode=disabled", "flavors.payout.processing-cron=-",
        "flavors.escrow.release-deadline-cron=-"})
class EscrowPayoutIT {
    @Autowired EscrowPayoutService payouts;
    @Autowired ControlledProvider provider;
    @Autowired ProofReviewService reviews;
    @Autowired EscrowReleaseOperationRepository operations;
    @Autowired JdbcTemplate jdbc;
    @Autowired EscrowReleaseDeadlineJob deadlineJob;
    @Autowired EscrowPayoutJob payoutJob;
    @Autowired com.flavors.service.PaymentService payments;
    private final List<Long> testOperationIds = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Providers {
        @Bean @Primary ControlledProvider controlledPayoutProvider() { return new ControlledProvider(); }
    }

    enum Mode { NORMAL, PENDING, UNKNOWN, TIMEOUT_AFTER_ACCEPT, CRASH_AFTER_ACCEPT, FAIL_RETRYABLE, FAIL_FINAL, WRONG_AMOUNT }

    static class Interruption extends Error {}

    static class ControlledProvider extends SimulatedPayoutProvider {
        final AtomicInteger submits = new AtomicInteger();
        final AtomicInteger queries = new AtomicInteger();
        volatile Mode mode = Mode.NORMAL;
        volatile boolean enabled = true;
        volatile boolean queryTimeout;
        volatile CountDownLatch entered;
        volatile CountDownLatch resume;
        volatile PayoutRequest lastRequest;
        volatile Runnable inspectCommittedClaim;

        @Override public boolean available() { return enabled; }

        @Override public PayoutResult submit(PayoutRequest request) {
            checkOutsideTransaction();
            submits.incrementAndGet();
            lastRequest = request;
            if (inspectCommittedClaim != null) inspectCommittedClaim.run();
            if (entered != null) { entered.countDown(); await(resume); }
            return switch (mode) {
                case NORMAL -> super.submit(request);
                case PENDING -> new PayoutResult(PayoutResult.Outcome.PENDING, super.submit(request).identity(),
                        "simulated:" + request.operationKey(), false);
                case UNKNOWN -> PayoutResult.unknown(request);
                case FAIL_RETRYABLE -> new PayoutResult(PayoutResult.Outcome.FAILED, request, null, true);
                case FAIL_FINAL -> new PayoutResult(PayoutResult.Outcome.FAILED, request, null, false);
                case TIMEOUT_AFTER_ACCEPT -> {
                    super.submit(request);
                    throw new IllegalStateException("response lost");
                }
                case CRASH_AFTER_ACCEPT -> {
                    super.submit(request);
                    throw new Interruption();
                }
                case WRONG_AMOUNT -> {
                    super.submit(request);
                    yield new PayoutResult(PayoutResult.Outcome.CONFIRMED,
                            new PayoutRequest(request.operationKey(), request.destinationRef(), request.amountCents() + 1, "USD"),
                            "simulated:" + request.operationKey(), false);
                }
            };
        }

        @Override public PayoutResult query(PayoutRequest request, String reference) {
            checkOutsideTransaction();
            queries.incrementAndGet();
            if (queryTimeout) throw new IllegalStateException("query timeout");
            return super.query(request, reference);
        }

        private void checkOutsideTransaction() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new AssertionError("Provider was called inside a transaction");
            }
        }
    }

    @BeforeEach
    void resetProvider() {
        provider.submits.set(0); provider.queries.set(0); provider.enabled = true;
        provider.mode = Mode.NORMAL; provider.queryTimeout = false;
        provider.entered = null; provider.resume = null; provider.inspectCommittedClaim = null;
    }

    @AfterEach
    void deferUnfinishedFixtures() {
        for (long id : testOperationIds) {
            jdbc.update("UPDATE escrow_release_operation SET next_attempt_at = now() + interval '1 day' "
                    + "WHERE id = ? AND status <> 'CONFIRMED'", id);
        }
    }

    @Test
    void simulationUsesStoredNetAndAtomicallyReleasesEscrowAndCompletesCampaign() {
        Fixture f = fixture();
        provider.inspectCommittedClaim = () -> {
            assertThat(state(f)).isEqualTo("UNKNOWN");
            assertThat(jdbc.queryForObject("SELECT lease_token IS NOT NULL FROM escrow_release_operation WHERE id = ?",
                    Boolean.class, f.operation())).isTrue();
            assertThat(paymentState(f)).isEqualTo("PENDING");
        };
        assertThat(payouts.processOperation(f.operation())).isTrue();
        assertThat(provider.lastRequest.amountCents()).isEqualTo(17003);
        assertThat(provider.lastRequest.operationKey()).isEqualTo("escrow-release:" + f.escrow());
        assertConfirmed(f);
        assertThat(payouts.processOperation(f.operation())).isFalse();
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(audits(f, "PAYOUT_CONFIRMED")).isEqualTo(1);
        assertThat(audits(f, "ESCROW_RELEASED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PAYOUT_CONFIRMED' "
                + "AND details->>'operationId' = ? AND details->>'simulated' = 'true'",
                Integer.class, Long.toString(f.operation()))).isEqualTo(1);
    }

    @Test
    void concurrentWorkersUseOneDispatchWhileTheFirstProviderCallIsInFlight() throws Exception {
        Fixture f = fixture();
        provider.entered = new CountDownLatch(1); provider.resume = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> payouts.processOperation(f.operation()));
        await(provider.entered);
        try {
            assertThat(payouts.processOperation(f.operation())).isFalse();
            assertThat(provider.submits.get()).isEqualTo(1);
        } finally { provider.resume.countDown(); }
        assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
        assertConfirmed(f);
    }

    @Test
    void expiredLeaseCannotApplyAnOldWorkersResponseOrCauseAnotherDispatch() throws Exception {
        Fixture f = fixture();
        provider.entered = new CountDownLatch(1); provider.resume = new CountDownLatch(1);
        var original = CompletableFuture.supplyAsync(() -> payouts.processOperation(f.operation()));
        await(provider.entered);
        try {
            due(f);
            assertThat(payouts.processOperation(f.operation())).isTrue(); // New worker queries; transfer not accepted yet.
            assertThat(state(f)).isEqualTo("UNKNOWN");
        } finally { provider.resume.countDown(); }
        assertThat(original.get(20, TimeUnit.SECONDS)).isFalse(); // Old lease cannot confirm.
        assertHeld(f);
        due(f);
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(2);
    }

    @Test
    void providerAcknowledgementKeepsFundsHeldUntilReconciliationConfirms() {
        Fixture f = fixture();
        provider.mode = Mode.PENDING;
        payouts.processOperation(f.operation());
        assertThat(state(f)).isEqualTo("SUBMITTED");
        assertHeld(f);
        assertThat(paymentState(f)).isEqualTo("PENDING");
        due(f);
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(1);
    }

    @Test
    void timeoutAfterAcceptanceReconcilesExistingTransferWithoutResubmitting() {
        Fixture f = fixture();
        provider.mode = Mode.TIMEOUT_AFTER_ACCEPT;
        payouts.processOperation(f.operation());
        assertThat(state(f)).isEqualTo("UNKNOWN");
        assertHeld(f);
        assertThat(payouts.processOperation(f.operation())).isFalse(); // Retry backoff respected.
        due(f);
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(1);
    }

    @Test
    void ambiguousNotFoundAndQueryTimeoutNeverAuthorizeAnotherSubmit() {
        Fixture f = fixture();
        provider.mode = Mode.UNKNOWN;
        payouts.processOperation(f.operation());
        for (int i = 0; i < 2; i++) {
            due(f);
            payouts.processOperation(f.operation());
        }
        provider.queryTimeout = true;
        due(f);
        payouts.processOperation(f.operation());
        assertThat(state(f)).isEqualTo("UNKNOWN");
        assertHeld(f);
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(3);
    }
    @Test
    void reconciliationStopsAfterTenQueries() {
        Fixture f = fixture();
        provider.mode = Mode.UNKNOWN;

        payouts.processOperation(f.operation());

        for (int i = 0; i < 10; i++) {
            due(f);
            assertThat(payouts.processOperation(f.operation())).isTrue();
        }

        due(f);
        assertThat(payouts.processOperation(f.operation())).isFalse();

        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(10);
        assertThat(operations.findById(f.operation()).orElseThrow()
                .getQueryAttempts()).isEqualTo(10);
        assertThat(state(f)).isEqualTo("UNKNOWN");
        assertHeld(f);
    }

    @Test
    void payoutPreparationDoesNotCreateIdempotencyRows() {
        Fixture f = fixture();

        payouts.processOperation(f.operation());

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM idempotency_key "
                        + "WHERE scope = 'payout-prepare'",
                Integer.class);

        assertThat(count).isZero();
        assertConfirmed(f);
    }
    @Test
    void submittedPayoutStopsAfterTenQueries() {
        Fixture f = fixture();
        provider.mode = Mode.PENDING;

        payouts.processOperation(f.operation());
        assertThat(state(f)).isEqualTo("SUBMITTED");

        jdbc.update(
                "UPDATE escrow_release_operation SET query_attempts = 10 WHERE id = ?",
                f.operation());
        due(f);

        assertThat(payouts.processOperation(f.operation())).isFalse();
        assertThat(provider.queries.get()).isZero();
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(state(f)).isEqualTo("SUBMITTED");
        assertHeld(f);
    }
    @Test
    void interruptionAfterProviderAcceptanceRecoversUsingTheDurableUnknownOperation() {
        Fixture f = fixture();
        provider.mode = Mode.CRASH_AFTER_ACCEPT;
        assertThatThrownBy(() -> payouts.processOperation(f.operation())).isInstanceOf(Interruption.class);
        assertThat(state(f)).isEqualTo("UNKNOWN");
        assertHeld(f);
        due(f); // Simulate lease expiry after process loss.
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(1);
    }

    @Test
    void retryableDefinitiveFailureReusesOperationAndPaymentTransaction() {
        Fixture f = fixture();
        provider.mode = Mode.FAIL_RETRYABLE;
        payouts.processOperation(f.operation());
        assertThat(state(f)).isEqualTo("FAILED");
        assertThat(paymentState(f)).isEqualTo("FAILED");
        assertHeld(f);
        Long paymentId = operations.findById(f.operation()).orElseThrow().getPaymentTransactionId();
        assertThat(payouts.processOperation(f.operation())).isFalse();
        provider.mode = Mode.NORMAL;
        due(f);
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(operations.findById(f.operation()).orElseThrow().getPaymentTransactionId()).isEqualTo(paymentId);
        assertThat(provider.lastRequest.operationKey()).isEqualTo("escrow-release:" + f.escrow());
        assertThat(provider.submits.get()).isEqualTo(2);
    }

    @Test
    void definitiveNonretryableFailureDoesNotResubmit() {
        Fixture f = fixture();
        provider.mode = Mode.FAIL_FINAL;
        payouts.processOperation(f.operation());
        due(f);
        assertThat(payouts.processOperation(f.operation())).isFalse();
        assertThat(state(f)).isEqualTo("FAILED");
        assertHeld(f);
        assertThat(provider.submits.get()).isEqualTo(1);
    }

    @Test
    void malformedConfirmationCannotReleaseTheWrongAmount() {
        Fixture f = fixture();
        provider.mode = Mode.WRONG_AMOUNT;
        payouts.processOperation(f.operation());
        assertThat(state(f)).isEqualTo("UNKNOWN");
        assertHeld(f);
        due(f);
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(provider.submits.get()).isEqualTo(1);
    }

    @Test
    void unavailableModeLeavesAuthorizationAndMoneyUntouched() {
        Fixture f = fixture();
        provider.enabled = false;
        assertThat(payouts.processOperation(f.operation())).isFalse();
        assertThat(payouts.processPendingPayouts()).isZero();
        assertThat(state(f)).isEqualTo("AUTHORIZED");
        assertThat(operations.findById(f.operation()).orElseThrow().getPaymentTransactionId()).isNull();
        assertThat(provider.submits.get()).isZero();
        assertHeld(f);
    }

    @Test
    void frozenRejectedAndEndedCampaignsNeverReachTheProvider() {
        Fixture frozen = fixture();
        jdbc.update("UPDATE escrow_payment SET status = 'FROZEN' WHERE id = ?", frozen.escrow());
        Fixture rejected = fixture();
        jdbc.update("UPDATE proof_review SET decision = 'REJECTED', reason = 'Rejected' WHERE submission_id = ?", rejected.submission());
        Fixture ended = fixture();
        jdbc.update("UPDATE campaign SET status = 'COMPLETED' WHERE id = ?", ended.campaign());
        for (Fixture f : List.of(frozen, rejected, ended)) {
            assertThat(payouts.processOperation(f.operation())).isFalse();
            assertThat(state(f)).isEqualTo("AUTHORIZED");
        }
        assertThat(provider.submits.get()).isZero();
        assertThat(provider.queries.get()).isZero();
    }

    @Test
    void auditFailureRollsBackAllFinancialConfirmationAndRecoveryDoesNotRepay() {
        Fixture f = fixture();
        jdbc.execute("CREATE FUNCTION step6_fail_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.action = 'PAYOUT_CONFIRMED' AND NEW.details->>'operationId' = '" + f.operation()
                + "' THEN RAISE EXCEPTION 'test confirmation interruption'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER step6_fail_confirmation BEFORE INSERT ON audit_log "
                + "FOR EACH ROW EXECUTE FUNCTION step6_fail_confirmation()");
        try {
            assertThatThrownBy(() -> payouts.processOperation(f.operation())).isInstanceOf(RuntimeException.class);
            assertThat(state(f)).isEqualTo("UNKNOWN");
            assertHeld(f);
            assertThat(paymentState(f)).isEqualTo("PENDING");
            assertThat(audits(f, "PAYOUT_CONFIRMED")).isZero();
            assertThat(audits(f, "ESCROW_RELEASED")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER step6_fail_confirmation ON audit_log");
            jdbc.execute("DROP FUNCTION step6_fail_confirmation()");
        }
        due(f);
        payouts.processOperation(f.operation());
        assertConfirmed(f);
        assertThat(provider.submits.get()).isEqualTo(1);
        assertThat(provider.queries.get()).isEqualTo(1);
    }

    @Test
    void scheduledBatchContinuesAfterOneFailedConfirmationAndLaterReconcilesIt() {
        Fixture failed = fixture();
        Fixture healthy = fixture();
        jdbc.execute("CREATE FUNCTION step6_batch_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.action = 'PAYOUT_CONFIRMED' AND NEW.details->>'operationId' = '" + failed.operation()
                + "' THEN RAISE EXCEPTION 'test batch interruption'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER step6_batch_failure BEFORE INSERT ON audit_log "
                + "FOR EACH ROW EXECUTE FUNCTION step6_batch_failure()");
        try {
            assertThat(payouts.processPendingPayouts()).isEqualTo(1);
            assertThat(state(failed)).isEqualTo("UNKNOWN");
            assertHeld(failed);
            assertConfirmed(healthy);
        } finally {
            jdbc.execute("DROP TRIGGER step6_batch_failure ON audit_log");
            jdbc.execute("DROP FUNCTION step6_batch_failure()");
        }
        due(failed);
        assertThat(payouts.processPendingPayouts()).isEqualTo(1);
        assertConfirmed(failed);
        assertThat(provider.submits.get()).isEqualTo(2);
        assertThat(provider.queries.get()).isEqualTo(1);
    }

    @Test
    void timerCAuthorizationUsesTheSamePayoutExecution() {
        Fixture f = rawFixture();
        long operation = operations.saveAndFlush(new EscrowReleaseOperation(f.escrow(), f.submission(), null,
                ReleaseAuthorizationSource.TIMER_C, 20003, Instant.now())).getId();
        testOperationIds.add(operation);
        Fixture timer = new Fixture(f.brand(), f.campaign(), f.escrow(), f.submission(), operation);
        payouts.processOperation(operation);
        assertConfirmed(timer);
    }

    @Test // TC-F7.2.2: actual deadline job -> payout job -> creator read model, without a synthetic approval.
    void unansweredFiveDayProofRunsThroughBothJobsAndShowsTheConfirmedNet() {
        Fixture raw = rawFixture();
        jdbc.update("UPDATE proof_submission SET submitted_at = now() - interval '5 days 1 second' WHERE id = ?",
                raw.submission());
        jdbc.update("UPDATE campaign SET proof_review_deadline = (SELECT submitted_at + interval '5 days' "
                + "FROM proof_submission WHERE id = ?) WHERE id = ?", raw.submission(), raw.campaign());
        deadlineJob.authorizeOverdueReleases();
        long id = operations.findByEscrowId(raw.escrow()).orElseThrow().getId();
        testOperationIds.add(id);
        Fixture f = new Fixture(raw.brand(), raw.campaign(), raw.escrow(), raw.submission(), id);
        assertThat(state(f)).isEqualTo("AUTHORIZED");
        assertHeld(f);
        payoutJob.processPayouts();
        assertConfirmed(f);
        long creator = jdbc.queryForObject("SELECT creator_id FROM campaign WHERE id = ?", Long.class, f.campaign());
        var response = payments.getEscrowForCreator(creator, f.campaign());
        assertThat(response.status()).isEqualTo(com.flavors.enums.EscrowStatus.RELEASED);
        assertThat(response.creatorNetCents()).isEqualTo(17003);
        assertThat(response.feeCents()).isEqualTo(3000);
        assertThat(response.simulated()).isTrue();
        assertThat(response.authorizationSource()).isEqualTo(ReleaseAuthorizationSource.TIMER_C);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_review WHERE submission_id = ?",
                Integer.class, f.submission())).isZero();
    }

    private void due(Fixture f) {
        jdbc.update("UPDATE escrow_release_operation SET next_attempt_at = null, "
                + "lease_until = CASE WHEN lease_token IS NOT NULL THEN last_attempt_at + interval '1 microsecond' END "
                + "WHERE id = ?", f.operation());
    }

    private String state(Fixture f) {
        return jdbc.queryForObject("SELECT status FROM escrow_release_operation WHERE id = ?", String.class, f.operation());
    }

    private String paymentState(Fixture f) {
        return jdbc.queryForObject("SELECT t.status FROM payment_transaction t JOIN escrow_release_operation o "
                + "ON t.id = o.payment_transaction_id WHERE o.id = ?", String.class, f.operation());
    }

    private void assertHeld(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?", String.class, f.escrow())).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT released_at IS NULL FROM escrow_payment WHERE id = ?", Boolean.class, f.escrow())).isTrue();
    }

    private void assertConfirmed(Fixture f) {
        assertThat(state(f)).isEqualTo("CONFIRMED");
        assertThat(paymentState(f)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?", String.class, f.escrow())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, f.campaign())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT fee_cents = 3000 AND creator_net_cents = 17003 AND released_at IS NOT NULL "
                + "FROM escrow_payment WHERE id = ?", Boolean.class, f.escrow())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transaction WHERE escrow_id = ? AND type = 'RELEASE' "
                + "AND amount_cents = 17003", Integer.class, f.escrow())).isEqualTo(1);
        var operation = operations.findById(f.operation()).orElseThrow();
        assertThat(operation.getOriginalAmountCents()).isEqualTo(20003);
        assertThat(operation.getFeeCents()).isEqualTo(3000);
        assertThat(operation.getCreatorNetCents()).isEqualTo(17003);
        assertThat(operation.getProviderRef()).startsWith("simulated:");
        assertThat(operation.getLeaseToken()).isNull();
    }

    private int audits(Fixture f, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ? AND details->>'operationId' = ?",
                Integer.class, action, Long.toString(f.operation()));
    }

    private Fixture fixture() {
        Fixture f = rawFixture();
        reviews.review(f.brand(), f.campaign(), f.submission(), new ProofReviewRequest(ProofReviewDecision.APPROVED, null));
        long operationId = operations.findByEscrowId(f.escrow()).orElseThrow().getId();
        testOperationIds.add(operationId);
        return new Fixture(f.brand(), f.campaign(), f.escrow(), f.submission(), operationId);
    }

    private Fixture rawFixture() {
        long brand = user("BRAND"); long creator = user("CREATOR");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'K', '1')", brand);
        jdbc.update("INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents) "
                + "VALUES (?, 'Creator', 'Food', 'Beirut', 0, 0)", creator);
        long campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                    start_date, last_posting_date, proof_review_deadline)
                VALUES (?, ?, 'Payout', 'PROOF_SUBMITTED', 20003, 20003, current_date, current_date,
                    now() + interval '5 days') RETURNING id
                """, Long.class, brand, creator);
        long escrow = jdbc.queryForObject("INSERT INTO escrow_payment (campaign_id, amount_cents, provider, status, held_at) "
                + "VALUES (?, 20003, 'test-only', 'HELD', now()) RETURNING id", Long.class, campaign);
        long submission = jdbc.queryForObject("INSERT INTO proof_submission (campaign_id, round, status, submitted_at) "
                + "VALUES (?, 1, 'SUBMITTED', now()) RETURNING id", Long.class, campaign);
        return new Fixture(brand, campaign, escrow, submission, 0);
    }

    private long user(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com", role);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private record Fixture(long brand, long campaign, long escrow, long submission, long operation) {}
}
