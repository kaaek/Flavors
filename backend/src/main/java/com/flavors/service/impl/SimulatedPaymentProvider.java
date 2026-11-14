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
 * The default provider for dev and tests (flavors.whish.mode=mock): a hosted checkout page inside Flavors, no
 * network and no money. The payer's choice on that page is kept in memory and reported through
 * {@link #queryStatus}, so the same code path runs as with a real provider. Off in the prod profile.
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
        return new HostedPaymentSession("/payments/simulated/" + request.reference());
    }

    @Override
    public ProviderPaymentStatus queryStatus(String reference, long amountCents, String currency) {
        return outcomes.getOrDefault(reference, ProviderPaymentStatus.PENDING);
    }

    /** The mock checkout page reports the payer's choice here. */
    public void setOutcome(String reference, ProviderPaymentStatus outcome) {
        outcomes.put(reference, outcome);
    }
}
