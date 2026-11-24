
package com.flavors.controller;

import com.flavors.service.EscrowPayoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payouts")
public class AdminPayoutController {

    private final EscrowPayoutService payouts;

    public AdminPayoutController(EscrowPayoutService payouts) {
        this.payouts = payouts;
    }

    public record ManualReviewRequest(
            @NotBlank
            @Size(max = 500)
            String evidenceReference,

            @NotBlank
            @Size(max = 1000)
            String finding
    ) {}

    @PostMapping("/{operationId}/review-evidence")
    public ResponseEntity<Void> recordEvidence(
            @PathVariable long operationId,
            @Valid @RequestBody ManualReviewRequest request) {

        payouts.recordManualReviewEvidence(
                operationId,
                request.evidenceReference(),
                request.finding()
        );

        return ResponseEntity.noContent().build();
    }
}
