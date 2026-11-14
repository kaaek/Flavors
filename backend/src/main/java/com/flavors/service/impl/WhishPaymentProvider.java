package com.flavors.service.impl;

import com.flavors.config.WhishProperties;
import com.flavors.dto.HostedPaymentRequest;
import com.flavors.dto.HostedPaymentSession;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.exception.PaymentProviderException;
import com.flavors.service.PaymentProvider;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Whish Money, collect-only (flavors.whish.mode=sandbox or live). Unofficial, from the community notes in
 * docs/README.md: the paths are configurable and every answer is read defensively.
 *
 * <p>Whish has no hold, refund or payout API: "held" is a Flavors ledger state, not money Whish keeps for us.
 * Credentials are sent as headers and never logged.
 */
@Component
@ConditionalOnExpression("'${flavors.whish.mode:mock}' != 'mock' and '${flavors.whish.mode:mock}' != 'disabled'")
public class WhishPaymentProvider implements PaymentProvider {

    private static final String PROVIDER_NAME = "WHISH";
    private static final String COLLECTED = "success";
    private static final String FAILED = "failed";

    private final WhishProperties properties;
    private final RestClient http;
    private final JsonMapper json;

    public WhishPaymentProvider(WhishProperties properties, RestClient.Builder builder, JsonMapper json) {
        requireSetting(properties.baseUrl(), "WHISH_BASE_URL");
        requireSetting(properties.channel(), "WHISH_CHANNEL");
        requireSetting(properties.secret(), "WHISH_SECRET");
        requireSetting(properties.websiteUrl(), "WHISH_WEBSITE_URL");
        this.properties = properties;
        this.json = json;
        this.http = builder
                .baseUrl(stripTrailingSlash(properties.baseUrl()))
                .defaultHeaders(headers -> {
                    headers.set("channel", properties.channel());
                    headers.set("secret", properties.secret());
                    headers.set("websiteurl", properties.websiteUrl());
                })
                .build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public HostedPaymentSession startHostedPayment(HostedPaymentRequest request) {
        Map<String, Object> body = Map.of(
                "amount", BigDecimal.valueOf(request.amountCents(), 2),
                "currency", request.currency(),
                "invoice", request.invoice(),
                "externalId", request.reference(),
                "successCallbackUrl", request.callbackUrl(),
                "failureCallbackUrl", request.callbackUrl(),
                "successRedirectUrl", request.redirectUrl(),
                "failureRedirectUrl", request.redirectUrl());
        Object answer = post(properties.createPath(), body);
        Object collectUrl = findValue(answer, "collectUrl");
        if (!(collectUrl instanceof String url) || url.isBlank()) {
            throw new PaymentProviderException("Whish did not return a collect URL.");
        }
        return new HostedPaymentSession(url);
    }

    /** Paid only when Whish says exactly "success". Anything else (including "status": false, code 500) is still pending. */
    @Override
    public ProviderPaymentStatus queryStatus(String reference, long amountCents, String currency) {
        Object answer = post(properties.statusPath(), Map.of("currency", currency, "externalId", reference));
        Object collectStatus = findValue(answer, "collectStatus");
        if (COLLECTED.equals(collectStatus)) {
            return ProviderPaymentStatus.PAID;
        }
        if (FAILED.equals(collectStatus)) {
            return ProviderPaymentStatus.FAILED;
        }
        return ProviderPaymentStatus.PENDING;
    }

    private Object post(String path, Map<String, Object> body) {
        String text;
        try {
            text = http.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new PaymentProviderException("Whish did not answer.", e);
        }
        try {
            return json.readValue(text == null ? "" : text, Object.class);
        } catch (RuntimeException e) {
            throw new PaymentProviderException("Whish answered with something that is not JSON.", e);
        }
    }

    /** The first value of this key anywhere in the answer, so both {@code {"collectUrl": ...}} and nested layouts work. */
    static Object findValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
            for (Object child : map.values()) {
                Object found = findValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof java.util.List<?> list) {
            for (Object child : list) {
                Object found = findValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when flavors.whish.mode is not mock.");
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
