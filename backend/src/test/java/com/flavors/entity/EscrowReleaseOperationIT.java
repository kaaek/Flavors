package com.flavors.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.repository.EscrowReleaseOperationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Real Flyway/PostgreSQL persistence. No providers, jobs, approval processing, or escrow-release orchestration. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EscrowReleaseOperationIT {

    @Autowired
    EscrowReleaseOperationRepository operations;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @ParameterizedTest
    @ValueSource(longs = {1, 10, 30, Long.MAX_VALUE})
    void databaseAndMoneyAgreeOnRoundingIncludingTheBigintLimit(long amount) {
        Fixture fixture = fixture(amount);
        operations.saveAndFlush(fixture.operation());
        var read = read(fixture);
        assertThat(read.getFeeCents()).isEqualTo(fixture.operation().getFeeCents());
        assertThat(read.getCreatorNetCents() + read.getFeeCents()).isEqualTo(amount);
    }

    @Test
    void persistsBothAuthorizationSourcesWithoutReleasingEscrowOrCreatingADispute() {
        Fixture timer = fixture(20000);
        var saved = operations.saveAndFlush(timer.operation());
        var read = operations.findByEscrowId(timer.escrow()).orElseThrow();
        assertThat(read.getId()).isEqualTo(saved.getId());
        assertThat(read.getStatus()).isEqualTo(PayoutStatus.AUTHORIZED);
        assertThat(read.getFeeCents()).isEqualTo(3000);
        assertThat(read.getCreatorNetCents()).isEqualTo(17000);
        assertThat(read.getOriginalAmountCents()).isEqualTo(20000);
        assertThat(read.getCurrency()).isEqualTo("USD");
        assertThat(read.getAuthorizedAt()).isEqualTo(timer.now());
        assertThat(read.getCreatedAt()).isNotNull();
        assertThat(read.getPaymentTransactionId()).isNull();
        assertThat(read.getProviderRef()).isNull();
        assertThat(operations.findByOperationKey(read.getOperationKey())).isPresent();

        Fixture approved = fixture(10);
        long review = jdbc.queryForObject("""
                INSERT INTO proof_review (submission_id, decision, decided_by)
                VALUES (?, 'APPROVED', ?) RETURNING id
                """, Long.class, approved.submission(), approved.brand());
        operations.saveAndFlush(new EscrowReleaseOperation(approved.escrow(), approved.submission(), review,
                ReleaseAuthorizationSource.APPROVAL, 10, approved.now()));
        assertThat(operations.findByEscrowId(approved.escrow()).orElseThrow().getFeeCents()).isEqualTo(2);
        assertEscrowHeld(timer);
        assertEscrowHeld(approved);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispute WHERE campaign_id IN (?, ?)", Integer.class,
                timer.campaign(), approved.campaign())).isZero();
    }

    @Test
    void persistsDispatchUncertaintyFailureRetryAndConfirmationWithoutMutatingEscrow() {
        Fixture fixture = fixture(20000);
        operations.saveAndFlush(fixture.operation());
        long transaction = releaseTransaction(fixture);
        mutate(fixture, o -> o.beginDispatch("SIMULATED", "synthetic-beneficiary", transaction,
                UUID.randomUUID(), fixture.now().plusSeconds(30), fixture.now()));
        assertThat(read(fixture).getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(read(fixture).getLeaseToken()).isNotNull();
        mutate(fixture, o -> o.recordFailed("TEMPORARY_FAILURE", true, fixture.now().plusSeconds(60), fixture.now()));
        assertThat(read(fixture).isRetryable()).isTrue();
        mutate(fixture, o -> o.beginDispatch("SIMULATED", "synthetic-beneficiary", transaction,
                UUID.randomUUID(), fixture.now().plusSeconds(90), fixture.now().plusSeconds(60)));
        mutate(fixture, o -> o.recordUnknown("TIMEOUT", fixture.now().plusSeconds(120), fixture.now().plusSeconds(60)));
        mutate(fixture, o -> o.recordQuery(fixture.now().plusSeconds(120)));
        mutate(fixture, o -> o.recordSubmitted("provider-reference", fixture.now().plusSeconds(120)));
        assertThat(read(fixture).getStatus()).isEqualTo(PayoutStatus.SUBMITTED);
        assertThat(read(fixture).getConfirmedAt()).isNull();
        mutate(fixture, o -> o.confirm("provider-reference", fixture.now().plusSeconds(180)));
        mutate(fixture, o -> o.confirm("provider-reference", fixture.now().plusSeconds(240)));
        var confirmed = read(fixture);
        assertThat(confirmed.getStatus()).isEqualTo(PayoutStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(fixture.now().plusSeconds(180));
        assertThat(confirmed.getSubmittedAt()).isEqualTo(fixture.now().plusSeconds(120));
        assertThat(confirmed.getDispatchAttempts()).isEqualTo(2);
        assertThat(confirmed.getQueryAttempts()).isEqualTo(1);
        assertThat(confirmed.getLeaseToken()).isNull();
        assertThat(operations.findByPayoutProviderAndProviderRef("SIMULATED", "provider-reference")).isPresent();
        // Later services must atomically apply authoritative confirmation to escrow and the transaction.
        // Persisting an operation alone has no financial side effects in Step 2.
        assertEscrowHeld(fixture);
    }

    @Test
    void concurrentAuthorizationsCreateOnlyOneOrdinaryOperationPerCampaign() throws Exception {
        Fixture fixture = fixture(20000);
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> saveAfter(start, fixture));
        var second = CompletableFuture.supplyAsync(() -> saveAfter(start, fixture));
        start.countDown();
        List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertThat(results.stream().filter(EscrowReleaseOperation.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(DataIntegrityViolationException.class::isInstance)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM escrow_release_operation WHERE escrow_id = ?",
                Integer.class, fixture.escrow())).isEqualTo(1);
    }

    @Test
    void rollingBackAnAuthorizationLeavesNoOperationAndDoesNotReleaseEscrow() {
        Fixture fixture = fixture(20000);
        tx.executeWithoutResult(s -> {
            operations.saveAndFlush(fixture.operation());
            s.setRollbackOnly();
        });
        assertThat(operations.findByEscrowId(fixture.escrow())).isEmpty();
        assertEscrowHeld(fixture);
        assertThat(operations.saveAndFlush(fixture.operation()).getId()).isNotNull();
    }

    @Test
    void databaseRefusesInvalidMoneySourceCurrencyForeignKeysAndDuplicateKeys() {
        Fixture fixture = fixture(20000);
        assertBadInsert(fixture, "fee_cents", "2999");
        assertBadInsert(fixture, "creator_net_cents", "16999");
        assertBadInsert(fixture, "fee_cents, creator_net_cents", "2999, 17001");
        assertBadInsert(fixture, "original_amount_cents", "0");
        assertBadInsert(fixture, "currency", "'EUR'");
        assertBadInsert(fixture, "authorization_source", "'APPROVAL'");
        assertBadInsert(fixture, "escrow_id", "-1");
        assertBadInsert(fixture, "submission_id", "-1");
        assertBadInsert(fixture, "review_id", "-1");
        assertBadInsert(fixture, "status", "'CONFIRMED'");
        assertBadInsert(fixture, "dispatch_attempts", "-1");
        assertBadInsert(fixture, "lease_token", "'00000000-0000-0000-0000-000000000001'");
        operations.saveAndFlush(fixture.operation());
        Fixture other = fixture(20000);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO escrow_release_operation
                  (escrow_id, submission_id, authorization_source, original_amount_cents, fee_cents,
                   creator_net_cents, operation_key, authorized_at, updated_at)
                VALUES (?, ?, 'TIMER_C', 20000, 3000, 17000, ?, now(), now())
                """, other.escrow(), other.submission(), "escrow-release:" + fixture.escrow()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseProtectsImmutableSnapshotsAndRejectsIllegalStateTransitions() {
        Fixture fixture = fixture(20000);
        long id = operations.saveAndFlush(fixture.operation()).getId();
        assertBadUpdate(id, "fee_cents = 2999, creator_net_cents = 17001");
        assertBadUpdate(id, "authorized_at = authorized_at + interval '1 second'");
        assertBadUpdate(id, "operation_key = 'different-key'");
        assertBadUpdate(id, "status = 'SUBMITTED', submitted_at = now()");
        assertBadUpdate(id, "status = 'CONFIRMED', confirmed_at = now()");
        assertBadUpdate(id, "status = 'INVALID'");
        long transaction = releaseTransaction(fixture);
        mutate(fixture, o -> o.beginDispatch("SIMULATED", "synthetic-beneficiary", transaction,
                UUID.randomUUID(), fixture.now().plusSeconds(30), fixture.now()));
        assertBadUpdate(id, "status = 'AUTHORIZED'");
        assertBadUpdate(id, "payout_provider = 'OTHER'");
        assertBadUpdate(id, "dispatch_attempts = 0");
        mutate(fixture, o -> o.recordSubmitted("unique-" + id, fixture.now()));
        mutate(fixture, o -> o.confirm("unique-" + id, fixture.now()));
        assertBadUpdate(id, "confirmed_at = confirmed_at + interval '1 second'");
        assertBadUpdate(id, "status = 'FAILED', confirmed_at = null");
    }

    @Test
    void providerReferenceIsUniqueWithinAProviderAndPaymentTransactionCannotBeReused() {
        Fixture first = fixture(20000);
        Fixture second = fixture(20000);
        operations.saveAndFlush(first.operation());
        operations.saveAndFlush(second.operation());
        long transaction = releaseTransaction(first);
        mutate(first, o -> o.beginDispatch("SIMULATED", "synthetic", transaction, UUID.randomUUID(),
                first.now().plusSeconds(30), first.now()));
        mutate(first, o -> o.recordSubmitted("shared-reference-" + first.escrow(), first.now()));
        assertThatThrownBy(() -> mutate(second, o -> o.beginDispatch("SIMULATED", "synthetic", transaction,
                UUID.randomUUID(), second.now().plusSeconds(30), second.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        long secondTransaction = releaseTransaction(second);
        mutate(second, o -> o.beginDispatch("SIMULATED", "synthetic", secondTransaction, UUID.randomUUID(),
                second.now().plusSeconds(30), second.now()));
        assertThatThrownBy(() -> mutate(second, o -> o.recordSubmitted("shared-reference-" + first.escrow(), second.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void mutate(Fixture fixture, java.util.function.Consumer<EscrowReleaseOperation> action) {
        tx.executeWithoutResult(s -> {
            // Persistence tests use the same campaign -> operation lock ordering as future services.
            jdbc.queryForObject("SELECT id FROM campaign WHERE id = ? FOR UPDATE", Long.class, fixture.campaign());
            action.accept(operations.findByEscrowIdForUpdate(fixture.escrow()).orElseThrow());
            operations.flush();
        });
    }

    private EscrowReleaseOperation read(Fixture fixture) {
        return operations.findByEscrowId(fixture.escrow()).orElseThrow();
    }

    private Object saveAfter(CountDownLatch start, Fixture fixture) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }
            return operations.saveAndFlush(fixture.operation());
        } catch (DataIntegrityViolationException e) {
            return e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertBadUpdate(long id, String assignments) {
        // Only literal test-owned SQL fragments are accepted by this helper.
        assertThatThrownBy(() -> jdbc.update("UPDATE escrow_release_operation SET " + assignments + " WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertBadInsert(Fixture fixture, String columns, String values) {
        // INSERT...SELECT excludes the overridden columns, so failures test constraints, not duplicate column syntax.
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("escrow_id", Long.toString(fixture.escrow()));
        fields.put("submission_id", Long.toString(fixture.submission()));
        fields.put("authorization_source", "'TIMER_C'");
        fields.put("original_amount_cents", "20000");
        fields.put("fee_cents", "3000");
        fields.put("creator_net_cents", "17000");
        fields.put("operation_key", "'invalid-' || gen_random_uuid()::text");
        fields.put("authorized_at", "now()");
        fields.put("updated_at", "now()");
        String[] keys = columns.split(", ");
        String[] replacements = values.split(", ");
        for (int i = 0; i < keys.length; i++) {
            fields.put(keys[i], replacements[i]);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO escrow_release_operation (" + String.join(",", fields.keySet())
                + ") SELECT " + String.join(",", fields.values())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertEscrowHeld(Fixture fixture) {
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE id = ?", String.class, fixture.escrow()))
                .isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT released_at FROM escrow_payment WHERE id = ?", Instant.class,
                fixture.escrow())).isNull();
    }

    private long releaseTransaction(Fixture fixture) {
        return jdbc.queryForObject("""
                INSERT INTO payment_transaction (escrow_id, type, status, amount_cents)
                VALUES (?, 'RELEASE', 'PENDING', ?) RETURNING id
                """, Long.class, fixture.escrow(), fixture.operation().getCreatorNetCents());
    }

    private Fixture fixture(long amount) {
        long brand = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'BRAND') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'K', '1')", brand);
        long creator = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'CREATOR') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Creator', 'Food', 'Beirut', 0, 0)
                """, creator);
        long campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                    start_date, last_posting_date)
                VALUES (?, ?, 'Release persistence', 'PROOF_SUBMITTED', ?, ?, current_date, current_date) RETURNING id
                """, Long.class, brand, creator, amount, amount);
        long escrow = jdbc.queryForObject("""
                INSERT INTO escrow_payment (campaign_id, amount_cents, provider, status, held_at)
                VALUES (?, ?, 'test-collection', 'HELD', now()) RETURNING id
                """, Long.class, campaign, amount);
        long submission = jdbc.queryForObject("""
                INSERT INTO proof_submission (campaign_id, round, status, submitted_at)
                VALUES (?, 1, 'SUBMITTED', now()) RETURNING id
                """, Long.class, campaign);
        return new Fixture(brand, campaign, escrow, submission, amount, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    private record Fixture(long brand, long campaign, long escrow, long submission, long amount, Instant now) {
        EscrowReleaseOperation operation() {
            return new EscrowReleaseOperation(escrow, submission, null, ReleaseAuthorizationSource.TIMER_C, amount, now);
        }
    }
}
