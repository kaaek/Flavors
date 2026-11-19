
package com.flavors.config;

import com.flavors.service.PayoutProvider;
import com.flavors.service.impl.SimulatedPayoutProvider;
import com.flavors.service.impl.UnavailablePayoutProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class PayoutConfigTest {

    private final PayoutConfig config = new PayoutConfig();

    @Test
    void stagingAllowsExplicitSimulation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");
        environment.setProperty("flavors.payout.mode", "simulated");

        PayoutProvider provider = config.payoutProvider(environment);

        assertThat(provider)
                .isInstanceOf(SimulatedPayoutProvider.class);

        assertThat(provider.simulated()).isTrue();
        assertThat(provider.available()).isTrue();
    }

    @Test
    void stagingDefaultsToDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        PayoutProvider provider = config.payoutProvider(environment);

        assertThat(provider)
                .isInstanceOf(UnavailablePayoutProvider.class);

        assertThat(provider.available()).isFalse();
    }

    @Test
    void productionRejectsSimulation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("flavors.payout.mode", "simulated");

        assertThatThrownBy(() -> config.payoutProvider(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forbidden in production");
    }

    @Test
    void productionWinsOverStaging() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging", "prod");
        environment.setProperty("flavors.payout.mode", "simulated");

        assertThatThrownBy(() -> config.payoutProvider(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownProfileCannotEnableSimulation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("other");
        environment.setProperty("flavors.payout.mode", "simulated");

        assertThatThrownBy(() -> config.payoutProvider(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentAllowsExplicitSimulation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("flavors.payout.mode", "simulated");

        assertThat(config.payoutProvider(environment))
                .isInstanceOf(SimulatedPayoutProvider.class);
    }
}
