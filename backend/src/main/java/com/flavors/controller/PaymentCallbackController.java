package com.flavors.controller;

import com.flavors.dto.PaymentResult;
import com.flavors.exception.PaymentProviderException;
import com.flavors.service.PaymentService;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The provider's callback and the payer's return. Both are public and unauthenticated, and both only ask Flavors to check the
 * payment's status with the provider: what they carry is never believed. Under /api/public, so no login and no CSRF token.
 */
@RestController
@RequestMapping("/api/public/payments")
public class PaymentCallbackController {

    private final PaymentService payments;

    public PaymentCallbackController(PaymentService payments) {
        this.payments = payments;
    }

    /** The provider reports a result. Unknown references get the same 200, so nothing about other payments leaks. */
    @GetMapping("/callback")
    ResponseEntity<Void> callback(@RequestParam("externalId") String externalId) {
        payments.reconcile(externalId);
        return ResponseEntity.ok().build();
    }

    /** The payer comes back from the provider's page: check the status, then send them to the campaign. */
    @GetMapping("/return")
    ResponseEntity<Void> returned(@RequestParam("externalId") String externalId) {
        Optional<PaymentResult> result;
        try {
            result = payments.reconcile(externalId);
        } catch (PaymentProviderException e) {
            // The status check failed; the sweep will check again. The campaign page shows what is known.
            result = Optional.empty();
        }
        String target = result
                .map(r -> "/brand/campaigns/" + r.campaignId() + "?payment=" + outcomeOf(r))
                .orElse("/brand/campaigns?payment=pending");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    private static String outcomeOf(PaymentResult result) {
        return switch (result.status()) {
            case SUCCEEDED -> result.held() ? "held" : "received";
            case FAILED -> "failed";
            case PENDING -> "pending";
        };
    }
}
