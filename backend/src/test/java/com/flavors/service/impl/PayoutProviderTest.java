package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.flavors.config.PayoutConfig;
import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PayoutProviderTest {
    private final PayoutRequest request = new PayoutRequest("escrow-release:1", "simulated-creator:7", 17000, "USD");

    @Test
    void simulationDeduplicatesByIdentityAndQueryNeverInventsALostLedgerEntry() {
        var provider = new SimulatedPayoutProvider();
        assertThat(provider.query(request, null).outcome()).isEqualTo(PayoutResult.Outcome.UNKNOWN);
        var first = provider.submit(request);
        assertThat(provider.submit(request)).isEqualTo(first);
        assertThat(provider.query(request, null)).isEqualTo(first);
        assertThat(first.providerRef()).startsWith("simulated:");
        assertThatThrownBy(() -> provider.submit(new PayoutRequest(request.operationKey(), "other", 17000, "USD")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new SimulatedPayoutProvider().query(request, first.providerRef()).outcome())
                .isEqualTo(PayoutResult.Outcome.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production", "prod,dev", "production,test", "prod,staging"})
    void simulationCannotStartOutsideDevelopmentOrTesting(String profiles) {
        new ApplicationContextRunner().withUserConfiguration(PayoutConfig.class)
                .withPropertyValues("spring.profiles.active=" + profiles, "flavors.payout.mode=simulated")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "staging"})
    void explicitSimulationIsAllowedInDevelopmentAndTests(String profile) {
        new ApplicationContextRunner().withUserConfiguration(PayoutConfig.class)
                .withPropertyValues("spring.profiles.active=" + profile, "flavors.payout.mode=simulated")
                .run(context -> assertThat(context.getBean(com.flavors.service.PayoutProvider.class))
                        .isInstanceOf(SimulatedPayoutProvider.class));
    }

    @Test
    void productionDefaultsDisabledAndNeitherCollectionModeNorRealModeEnablesPayouts() {
        new ApplicationContextRunner().withUserConfiguration(PayoutConfig.class)
                .withPropertyValues("spring.profiles.active=prod", "flavors.whish.mode=live")
                .run(context -> {
                    var provider = context.getBean(com.flavors.service.PayoutProvider.class);
                    assertThat(provider.available()).isFalse();
                    assertThat(provider.submit(request).outcome()).isEqualTo(PayoutResult.Outcome.UNKNOWN);
                });
        new ApplicationContextRunner().withUserConfiguration(PayoutConfig.class)
                .withPropertyValues("flavors.payout.mode=live")
                .run(context -> assertThat(context).hasFailed());
    }
}
