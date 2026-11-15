package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.dto.PaymentResult;
import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.service.impl.PaymentServiceImpl.StartAction;
import org.junit.jupiter.api.Test;

/**
 * What a Pay click does once the provider has been asked about the attempt it would reuse. The expected actions are the
 * agreed contract for gap 3: paid in time is "already paid", still waiting reopens, ended without payment opens a fresh
 * attempt, and money received after the window is never reported as held.
 */
class PaymentStartActionTest {

    private static PaymentResult result(PaymentTransactionStatus status, boolean held) {
        return new PaymentResult(42L, status, held);
    }

    @Test
    void aPaymentHeldInEscrowIsAlreadyPaid() {
        assertThat(PaymentServiceImpl.actionFor(result(PaymentTransactionStatus.SUCCEEDED, true)))
                .isEqualTo(StartAction.ALREADY_PAID);
    }

    @Test
    void aPaymentReceivedButNotHeldIsNeverCalledPaid() {
        assertThat(PaymentServiceImpl.actionFor(result(PaymentTransactionStatus.SUCCEEDED, false)))
                .isEqualTo(StartAction.RECEIVED_NOT_HELD);
    }

    @Test
    void anAttemptStillWaitingReopensItsCheckout() {
        assertThat(PaymentServiceImpl.actionFor(result(PaymentTransactionStatus.PENDING, false)))
                .isEqualTo(StartAction.REOPEN);
    }

    @Test
    void anAttemptTheProviderEndedOpensAFreshOne() {
        assertThat(PaymentServiceImpl.actionFor(result(PaymentTransactionStatus.FAILED, false)))
                .isEqualTo(StartAction.NEW_ATTEMPT);
    }
}
