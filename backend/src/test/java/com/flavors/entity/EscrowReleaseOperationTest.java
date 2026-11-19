package com.flavors.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EscrowReleaseOperationTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @ParameterizedTest
    @CsvSource({"20000,3000,17000", "10,2,8", "1,0,1", "30,5,25", "9223372036854775807,1383505805528216371,7839866231326559436"})
    void snapshotsTheFeeAndSubtractsItWithoutLosingACent(long amount, long fee, long net) {
        var operation = operation(amount);

        assertThat(operation.getFeeCents()).isEqualTo(fee);
        assertThat(operation.getCreatorNetCents()).isEqualTo(net);
        assertThat(operation.getFeeCents() + operation.getCreatorNetCents()).isEqualTo(amount);
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.AUTHORIZED);
        assertThat(operation.getOperationKey()).isEqualTo("escrow-release:1");
    }

    @Test
    void approvalRequiresAReviewAndTimerCDoesNotInventOne() {
        assertThatThrownBy(() -> new EscrowReleaseOperation(1, 2, null,
                ReleaseAuthorizationSource.APPROVAL, 20000, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EscrowReleaseOperation(1, 2, 3L,
                ReleaseAuthorizationSource.TIMER_C, 20000, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new EscrowReleaseOperation(1, 2, 3L, ReleaseAuthorizationSource.APPROVAL, 20000, NOW)
                .getReviewId()).isEqualTo(3);
    }

    @Test
    void unknownOutcomeCannotBeBlindlyDispatchedAgainAndConfirmationIsIdempotent() {
        var operation = operation(20000);
        assertThatThrownBy(() -> operation.confirm("ref", NOW)).isInstanceOf(IllegalStateException.class);
        dispatch(operation, NOW);
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThatThrownBy(() -> dispatch(operation, NOW)).isInstanceOf(IllegalStateException.class);
        operation.recordUnknown("TIMEOUT", NOW.plusSeconds(60), NOW);
        operation.recordQuery(NOW.plusSeconds(60));
        operation.recordSubmitted("ref", NOW.plusSeconds(60));
        assertThat(operation.getConfirmedAt()).isNull();
        operation.confirm("ref", NOW.plusSeconds(120));
        operation.confirm("ref", NOW.plusSeconds(180));
        assertThat(operation.getConfirmedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(operation.getDispatchAttempts()).isEqualTo(1);
        assertThat(operation.getQueryAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> operation.confirm("different", NOW.plusSeconds(180)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retriesRequireDefinitiveFailureAndPreserveMoneyIdentityAndAuthorizationTime() {
        var operation = operation(20000);
        dispatch(operation, NOW);
        operation.recordFailed("DECLINED", false, null, NOW);
        assertThatThrownBy(() -> dispatch(operation, NOW)).isInstanceOf(IllegalStateException.class);

        var retryable = operation(20000);
        dispatch(retryable, NOW);
        retryable.recordFailed("TEMPORARY_FAILURE", true, NOW.plusSeconds(60), NOW);
        assertThatThrownBy(() -> retryable.beginDispatch("OTHER", "beneficiary", 3,
                UUID.randomUUID(), NOW.plusSeconds(120), NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
        dispatch(retryable, NOW.plusSeconds(60));
        assertThat(retryable.getDispatchAttempts()).isEqualTo(2);
        assertThat(retryable.getAuthorizedAt()).isEqualTo(NOW);
        assertThat(retryable.getCreatorNetCents()).isEqualTo(17000);
    }

    @Test
    void definitiveNotFoundAllowsRetryWithSameOperationKey() {
        var operation = operation(20000);

        String originalKey = operation.getOperationKey();
        long originalNet = operation.getCreatorNetCents();

        // The payout was prepared but its outcome is unknown.
        dispatch(operation, NOW);

        assertThat(operation.getStatus())
                .isEqualTo(PayoutStatus.UNKNOWN);

        // The provider has authoritatively confirmed no payout exists.
        operation.recordFailed(
                "PROVIDER_NOT_FOUND",
                true,
                NOW.plusSeconds(60),
                NOW);

        assertThat(operation.getStatus())
                .isEqualTo(PayoutStatus.FAILED);
        assertThat(operation.isRetryable()).isTrue();

        // Retry after the scheduled delay.
        dispatch(operation, NOW.plusSeconds(60));

        assertThat(operation.getStatus())
                .isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(operation.getDispatchAttempts()).isEqualTo(2);
        assertThat(operation.getOperationKey()).isEqualTo(originalKey);
        assertThat(operation.getCreatorNetCents()).isEqualTo(originalNet);
    }

    @Test
    void preparingQueryDoesNotIncrementQueryAttempts() {
        var operation = operation(20000);

        dispatch(operation, NOW);

        operation.beginQuery(
                UUID.randomUUID(),
                NOW.plusSeconds(120),
                NOW.plusSeconds(60));

        // Preparation alone must not count as a provider query.
        assertThat(operation.getQueryAttempts()).isZero();

        // Record the query only after the provider call was attempted.
        operation.recordQuery(NOW.plusSeconds(60));

        assertThat(operation.getQueryAttempts()).isEqualTo(1);
    }

    @Test
    void payoutRemainsReconciliableAfterTenQueries() {
        var operation = operation(20000);

        dispatch(operation, NOW);

        // Simulate ten unsuccessful reconciliation attempts.
        for (int i = 1; i <= 10; i++) {
            Instant attemptTime = NOW.plusSeconds(i * 60L);

            operation.beginQuery(
                    UUID.randomUUID(),
                    attemptTime.plusSeconds(30),
                    attemptTime);

            operation.recordQuery(attemptTime);

            operation.recordUnknown(
                    "PROVIDER_OUTCOME_UNKNOWN",
                    attemptTime.plusSeconds(60),
                    attemptTime);
        }

        assertThat(operation.getQueryAttempts()).isEqualTo(10);
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);

        // Escalate without ending reconciliation.
        Instant escalationTime = NOW.plusSeconds(600);
        operation.markNeedsReview(escalationTime);

        assertThat(operation.getNeedsReviewAt())
                .isEqualTo(escalationTime);

        // An eleventh query must still be allowed.
        Instant nextAttempt = NOW.plusSeconds(660);

        operation.beginQuery(
                UUID.randomUUID(),
                nextAttempt.plusSeconds(30),
                nextAttempt);

        operation.recordQuery(nextAttempt);

        operation.recordUnknown(
                "PROVIDER_OUTCOME_UNKNOWN",
                nextAttempt.plusSeconds(60),
                nextAttempt);

        assertThat(operation.getQueryAttempts()).isEqualTo(11);
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);

        // The original escalation timestamp is preserved.
        assertThat(operation.getNeedsReviewAt())
                .isEqualTo(escalationTime);

        // Payout identity remains unchanged.
        assertThat(operation.getOperationKey())
                .isEqualTo("escrow-release:1");
    }

    private static EscrowReleaseOperation operation(long amount) {
        return new EscrowReleaseOperation(1, 2, null, ReleaseAuthorizationSource.TIMER_C, amount, NOW);
    }

    private static void dispatch(EscrowReleaseOperation operation, Instant now) {
        operation.beginDispatch("SIMULATED", "beneficiary", 3, UUID.randomUUID(), now.plusSeconds(30), now);
    }
}
