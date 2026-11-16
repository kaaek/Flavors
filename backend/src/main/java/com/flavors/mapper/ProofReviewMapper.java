package com.flavors.mapper;

import com.flavors.dto.ProofReviewResponse;
import com.flavors.entity.ProofReview;
import com.flavors.enums.ProofReviewFollowUp;
import org.springframework.stereotype.Component;

@Component
public class ProofReviewMapper {

    public ProofReviewResponse toResponse(ProofReview review) {
        ProofReviewFollowUp followUp = switch (review.getDecision()) {
            case APPROVED -> ProofReviewFollowUp.RELEASE_PAYMENT;
            case REJECTED -> ProofReviewFollowUp.FREEZE_PAYMENT_AND_OPEN_DISPUTE;
            case CORRECTIONS_REQUESTED -> ProofReviewFollowUp.RESUBMIT_PROOF;
        };
        return new ProofReviewResponse(review.getId(), review.getSubmissionId(), review.getDecision(),
                review.getReason(), review.getDecidedAt(), followUp);
    }
}
