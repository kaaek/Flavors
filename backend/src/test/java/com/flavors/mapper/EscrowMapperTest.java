package com.flavors.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.entity.EscrowPayment;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EscrowMapperTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private final EscrowMapper mapper = new EscrowMapper();

    @ParameterizedTest
    @EnumSource(PayoutStatus.class)
    void mapsTheStoredSnapshotAndPublicProgressWithoutRecalculating(PayoutStatus status) {
        var escrow = new EscrowPayment(42L, 20003, "collection", NOW);
        escrow.hold(NOW);
        var operation = new EscrowReleaseOperation(10, 5, 8L, ReleaseAuthorizationSource.APPROVAL, 20003, NOW);
        if (status != PayoutStatus.AUTHORIZED) {
            operation.beginDispatch("SIMULATED", "private-destination", 9, UUID.randomUUID(), NOW.plusSeconds(120), NOW);
            switch (status) {
                case SUBMITTED -> operation.recordSubmitted("private-reference", NOW);
                case FAILED -> operation.recordFailed("PRIVATE_ERROR", false, null, NOW);
                case CONFIRMED -> {
                    operation.confirm("private-reference", NOW);
                    escrow.release(operation.getFeeCents(), operation.getCreatorNetCents(), NOW);
                }
                default -> { }
            }
        }
        var response = mapper.toResponse(escrow, operation, true, true);
        assertThat(response.amountCents()).isEqualTo(20003);
        assertThat(response.feeCents()).isEqualTo(3000);
        assertThat(response.creatorNetCents()).isEqualTo(17003);
        assertThat(response.payoutStatus()).isEqualTo(status);
        assertThat(response.authorizationSource()).isEqualTo(ReleaseAuthorizationSource.APPROVAL);
        assertThat(response.authorizedAt()).isEqualTo(NOW);
        assertThat(response.simulated()).isTrue();
        assertThat(response.payoutAvailable()).isTrue();
    }

    @Test
    void missingOperationLeavesPayoutDetailsNullableAndUnavailableIsTruthful() {
        var escrow = new EscrowPayment(42L, 20003, "collection", NOW);
        var response = mapper.toResponse(escrow, null, false, false);
        assertThat(response.feeCents()).isNull();
        assertThat(response.creatorNetCents()).isNull();
        assertThat(response.payoutStatus()).isNull();
        assertThat(response.authorizedAt()).isNull();
        assertThat(response.simulated()).isNull();
        assertThat(response.payoutAvailable()).isFalse();
    }
}
