package com.flavors.dto;

import com.flavors.enums.ProofReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Rejections require a non-blank reason; the service enforces that conditional rule. */
public record ProofReviewRequest(
        @NotNull(message = "Choose approve, request corrections or reject.") ProofReviewDecision decision,
        @Size(max = 2000, message = "The reason cannot exceed 2000 characters.") String reason) {
}
