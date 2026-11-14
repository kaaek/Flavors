package com.flavors.controller;

import com.flavors.dto.PaymentCheckout;
import com.flavors.dto.SimulatedCompleteRequest;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.exception.ValidationFailedException;
import com.flavors.service.PaymentService;
import com.flavors.service.impl.SimulatedPaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The mock checkout's API (flavors.whish.mode=mock, never in prod). The payer's button sets the outcome in the mock
 * provider and then runs the same status check the real provider's callback would trigger.
 */
@RestController
@Profile("!prod")
@ConditionalOnProperty(name = "flavors.whish.mode", havingValue = "mock", matchIfMissing = true)
@RequestMapping("/api/public/payments/simulated")
public class SimulatedCheckoutController {

    private final PaymentService payments;
    private final SimulatedPaymentProvider simulated;

    public SimulatedCheckoutController(PaymentService payments, SimulatedPaymentProvider simulated) {
        this.payments = payments;
        this.simulated = simulated;
    }

    @GetMapping("/{reference}")
    PaymentCheckout checkout(@PathVariable String reference) {
        return payments.checkout(reference);
    }

    @PostMapping("/{reference}/complete")
    PaymentCheckout complete(@PathVariable String reference, @RequestBody SimulatedCompleteRequest request) {
        ProviderPaymentStatus outcome = request.outcome();
        if (outcome != ProviderPaymentStatus.PAID && outcome != ProviderPaymentStatus.FAILED) {
            throw new ValidationFailedException("outcome", "Use PAID or FAILED.");
        }
        simulated.setOutcome(reference, outcome);
        payments.reconcile(reference);
        return payments.checkout(reference);
    }
}
