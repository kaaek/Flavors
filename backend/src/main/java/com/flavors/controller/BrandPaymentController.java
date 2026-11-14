package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.EscrowResponse;
import com.flavors.dto.PaymentStartResponse;
import com.flavors.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The brand pays for a campaign it accepted (UC-F7.1). Under /api/brand, so only the BRAND role gets in. */
@RestController
@RequestMapping("/api/brand/campaigns/{campaignId}")
public class BrandPaymentController {

    private final PaymentService payments;

    public BrandPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Starts the payment. Send a new Idempotency-Key for each Pay click; a retry with the same key is the same request. */
    @PostMapping("/payment")
    PaymentStartResponse start(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId,
                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return payments.startPayment(user.getId(), campaignId, idempotencyKey);
    }

    /** The escrow status (FR-F7.7). */
    @GetMapping("/escrow")
    EscrowResponse escrow(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId) {
        return payments.getEscrowForBrand(user.getId(), campaignId);
    }
}
