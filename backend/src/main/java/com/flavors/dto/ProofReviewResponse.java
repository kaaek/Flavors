package com.flavors.dto;

import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofReviewFollowUp;
import java.time.Instant;

/** Records a decision, not a payment/dispute completion. Follow-up integrations are deferred. */
public record ProofReviewResponse(long id, long submissionId, ProofReviewDecision decision, String reason,
                                  Instant decidedAt, ProofReviewFollowUp requiredFollowUp) {
}
