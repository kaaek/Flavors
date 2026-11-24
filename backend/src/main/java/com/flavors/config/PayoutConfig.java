
package com.flavors.config;

import com.flavors.service.PayoutProvider;
import com.flavors.service.impl.SimulatedPayoutProvider;
import com.flavors.service.impl.UnavailablePayoutProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class PayoutConfig {

    @Bean
    public PayoutProvider payoutProvider(Environment environment) {
        String mode = environment.getProperty(
                "flavors.payout.mode", "disabled");

        // Payouts remain disabled unless explicitly enabled.
        if ("disabled".equals(mode)) {
            return new UnavailablePayoutProvider();
        }

        // No real payout adapter has been implemented yet.
        if (!"simulated".equals(mode)) {
            throw new IllegalStateException(
                    "No verified real payout adapter is available.");
        }

        // Simulation is forbidden in production, even if
        // another allowed profile is also active.
        if (environment.acceptsProfiles(
                Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "Simulated payouts are forbidden in production.");
        }

        // Simulation is only allowed in dev, test, or staging.
        if (!environment.acceptsProfiles(
                Profiles.of("dev", "test", "staging"))) {
            throw new IllegalStateException(
                    "Simulated payouts require dev, test, or staging.");
        }

        return new SimulatedPayoutProvider();
    }
}
