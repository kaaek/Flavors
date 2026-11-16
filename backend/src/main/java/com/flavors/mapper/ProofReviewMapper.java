package com.flavors.mapper;

import com.flavors.dto.ProofReviewAgreementResponse;
import com.flavors.dto.ProofReviewResponse;
import com.flavors.entity.Campaign;
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

    /**
     * The terms the brand checks the proof against (FR-F6.1), read from the campaign. They are frozen once the
     * campaign is sent, so this is what the creator agreed to. The caller loads the campaign in its transaction.
     */
    public ProofReviewAgreementResponse toAgreement(Campaign campaign) {
        return new ProofReviewAgreementResponse(campaign.getStartDate(), campaign.getLastPostingDate(),
                campaign.getContentRequirements(), campaign.isScreenshotsRequired(),
                campaign.getDeliverables().stream()
                        .map(d -> new ProofReviewAgreementResponse.Deliverable(d.getPlatform(), d.getType(),
                                d.getCount()))
                        .toList());
    }
}
