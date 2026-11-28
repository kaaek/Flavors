package com.flavors.platform.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code flavors.platform.outbox.*}
 *
 * @param exchange     the topic exchange events are published to (every service uses the same one)
 * @param relayEnabled run the relay in this process; false for tests that drive it by hand
 * @param pollInterval pause between relay runs
 * @param batchSize    rows published per run
 * @param confirmTimeout how long to wait for the broker to confirm one message
 */
@ConfigurationProperties("flavors.platform.outbox")
public record OutboxProperties(
        String exchange,
        Boolean relayEnabled,
        Duration pollInterval,
        Integer batchSize,
        Duration confirmTimeout) {

    public OutboxProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "flavors.events";
        }
        if (relayEnabled == null) {
            relayEnabled = true;
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(1);
        }
        if (batchSize == null) {
            batchSize = 100;
        }
        if (confirmTimeout == null) {
            confirmTimeout = Duration.ofSeconds(5);
        }
    }
}
