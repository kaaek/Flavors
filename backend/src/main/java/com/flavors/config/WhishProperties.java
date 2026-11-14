package com.flavors.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Whish settings, all from the environment and never committed (docs/README.md).
 *
 * @param mode       {@code mock} (default: no network, no money; dev and tests), {@code sandbox} or
 *                   {@code live} (call Whish at {@code baseUrl}), or {@code disabled} (no payments; production default)
 * @param baseUrl    Whish API base, e.g. https://lb.sandbox.whish.money/itel-service/api
 * @param channel    merchant channel, sent as the {@code channel} header
 * @param secret     merchant secret, sent as the {@code secret} header
 * @param websiteUrl merchant website, sent as the {@code websiteurl} header
 * @param createPath path that creates a payment (configurable: the official spec path is not public)
 * @param statusPath path that reports a payment's status
 */
@ConfigurationProperties(prefix = "flavors.whish")
public record WhishProperties(
        @DefaultValue("mock") String mode,
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String channel,
        @DefaultValue("") String secret,
        @DefaultValue("") String websiteUrl,
        @DefaultValue("/payment/whish") String createPath,
        @DefaultValue("/payment/collect/status") String statusPath) {
}
