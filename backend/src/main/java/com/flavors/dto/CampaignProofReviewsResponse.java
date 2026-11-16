package com.flavors.dto;

import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofReviewDecision;
import java.time.Instant;
import java.util.List;

/** Review history plus the decisions still available for the latest submitted version. */
public record CampaignProofReviewsResponse(long campaignId, CampaignStatus campaignStatus, int correctionRoundsUsed,
        Instant reviewDeadline, Long latestSubmissionId, ProofReviewAgreementResponse agreement,
        List<ProofReviewDecision> availableDecisions,
        List<ProofReviewResponse> reviews) {
}
