package com.flavors.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.enums.EscrowStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class EscrowPaymentTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void freezingPreservesBalanceAndCollectionIdentityWithoutReleasingFunds() {
        var escrow = new EscrowPayment(42L, 20000, "test-only", NOW.minusSeconds(60));
        escrow.attachProviderPayment("collection-ref", NOW.minusSeconds(60));
        escrow.hold(NOW.minusSeconds(30));
        escrow.freeze(NOW);
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.FROZEN);
        assertThat(escrow.getAmountCents()).isEqualTo(20000);
        assertThat(escrow.getHeldAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(escrow.getReleasedAt()).isNull();
        assertThat(escrow.getUpdatedAt()).isEqualTo(NOW);
        assertThat(escrow.getProviderPaymentId()).isEqualTo("collection-ref");
        for (String field : new String[]{"feeCents", "creatorNetCents", "refundedCents"}) {
            assertThat(ReflectionTestUtils.getField(escrow, field)).isEqualTo(0L);
        }
    }

    @ParameterizedTest
    @EnumSource(value = EscrowStatus.class, names = "HELD", mode = EnumSource.Mode.EXCLUDE)
    void onlyHeldEscrowCanTransitionToFrozen(EscrowStatus status) {
        var escrow = new EscrowPayment(42L, 20000, "test-only", NOW);
        ReflectionTestUtils.setField(escrow, "status", status);
        assertThatThrownBy(() -> escrow.freeze(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(escrow.getStatus()).isEqualTo(status);
    }

    @Test
    void confirmedReleaseCopiesTheSnapshotAndCannotReleaseFrozenEscrow() {
        var escrow = new EscrowPayment(42L, 20003, "test-only", NOW);
        escrow.hold(NOW);
        assertThatThrownBy(() -> escrow.release(3000, 17000, NOW)).isInstanceOf(IllegalStateException.class);
        escrow.release(3000, 17003, NOW);
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(escrow.getReleasedAt()).isEqualTo(NOW);
        assertThat(ReflectionTestUtils.getField(escrow, "feeCents")).isEqualTo(3000L);
        var frozen = new EscrowPayment(43L, 20000, "test-only", NOW);
        frozen.hold(NOW);
        frozen.freeze(NOW);
        assertThatThrownBy(() -> frozen.release(3000, 17000, NOW)).isInstanceOf(IllegalStateException.class);
    }
}
