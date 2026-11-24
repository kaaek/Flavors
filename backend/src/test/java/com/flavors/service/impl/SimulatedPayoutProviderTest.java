
package com.flavors.service.impl;

import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SimulatedPayoutProviderTest {

    private PayoutRequest request() {
        return new PayoutRequest(
                "escrow-release:10",
                "simulated-creator:5",
                8500L,
                "USD");
    }

    @Test
    void repeatedSubmissionUsesSamePayoutReference() {
        SimulatedPayoutProvider provider =
                new SimulatedPayoutProvider();

        PayoutRequest request = request();

        PayoutResult first = provider.submit(request);
        PayoutResult second = provider.submit(request);

        assertThat(first.outcome())
                .isEqualTo(PayoutResult.Outcome.CONFIRMED);

        assertThat(second.outcome())
                .isEqualTo(PayoutResult.Outcome.CONFIRMED);

        assertThat(second.providerRef())
                .isEqualTo(first.providerRef());
    }

    @Test
    void existingPayoutCanBeReconciled() {
        SimulatedPayoutProvider provider =
                new SimulatedPayoutProvider();

        PayoutRequest request = request();
        provider.submit(request);

        PayoutResult result = provider.query(request, null);

        assertThat(result.outcome())
                .isEqualTo(PayoutResult.Outcome.CONFIRMED);
    }

    @Test
    void missingLedgerEntryRemainsUnknown() {
        SimulatedPayoutProvider provider =
                new SimulatedPayoutProvider();

        PayoutResult result = provider.query(request(), null);

        assertThat(result.outcome())
                .isEqualTo(PayoutResult.Outcome.UNKNOWN);
    }

    @Test
    void newSimulatorCannotConfirmPreviousPayout() {
        SimulatedPayoutProvider previous =
                new SimulatedPayoutProvider();

        PayoutRequest request = request();
        previous.submit(request);

        // Simulates losing the in-memory ledger after restart.
        SimulatedPayoutProvider restarted =
                new SimulatedPayoutProvider();

        PayoutResult result = restarted.query(request, null);

        assertThat(result.outcome())
                .isEqualTo(PayoutResult.Outcome.UNKNOWN);
    }

    @Test
    void notFoundCanRepresentAuthoritativeProviderResponse() {
        PayoutResult result = PayoutResult.notFound(request());

        assertThat(result.outcome())
                .isEqualTo(PayoutResult.Outcome.NOT_FOUND);

        assertThat(result.providerRef()).isNull();
        assertThat(result.safeToRetry()).isFalse();
    }
}
