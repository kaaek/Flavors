package com.flavors.service.impl;

import com.flavors.dto.HostedPaymentRequest;
import com.flavors.dto.HostedPaymentSession;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.service.PaymentProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The default provider for dev and tests (flavors.whish.mode=mock): no network and no money. Its checkout is the
 * return URL, so a payer who follows it is only asked for the status and stays pending. Outcomes are set in code
 * through {@link #setOutcome}, so no HTTP path can mark a payment paid. Off in the prod profile.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "flavors.whish.mode", havingValue = "mock", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProvider {

    private final Map<String, ProviderPaymentStatus> outcomes = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "WHISH";
    }

    @Override
    public HostedPaymentSession startHostedPayment(HostedPaymentRequest request) {
        return new HostedPaymentSession(request.redirectUrl());
    }

    @Override
    public ProviderPaymentStatus queryStatus(String reference, long amountCents, String currency) {
        return outcomes.getOrDefault(reference, ProviderPaymentStatus.PENDING);
    }

    /** Tests set the outcome the next status check reports. */
    public void setOutcome(String reference, ProviderPaymentStatus outcome) {
        outcomes.put(reference, outcome);
    }
}
