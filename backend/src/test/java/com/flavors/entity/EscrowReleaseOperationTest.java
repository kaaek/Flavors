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

    private static EscrowReleaseOperation operation(long amount) {
        return new EscrowReleaseOperation(1, 2, null, ReleaseAuthorizationSource.TIMER_C, amount, NOW);
    }

    private static void dispatch(EscrowReleaseOperation operation, Instant now) {
        operation.beginDispatch("SIMULATED", "beneficiary", 3, UUID.randomUUID(), now.plusSeconds(30), now);
    }
}
