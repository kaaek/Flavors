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
        String mode = environment.getProperty("flavors.payout.mode", "disabled");
        if ("disabled".equals(mode)) return new UnavailablePayoutProvider();
        if (!"simulated".equals(mode)) throw new IllegalStateException("No verified real payout adapter is available.");
        // A production profile wins even if dev/test is also active.
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))
                || !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("Simulated payouts require dev/test and are forbidden in production.");
        }
        return new SimulatedPayoutProvider();
    }
}
