package com.flavors.dto;

import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofSubmissionStatus;
import java.time.Instant;
import java.util.List;

/** The proof state visible to the current role; drafts are returned only to their creator. */
public record DeliveryProofResponse(
        long campaignId,
        String campaignName,
        CampaignStatus campaignStatus,
        Instant reviewDeadline,
        List<Submission> submissions) {

    public record Submission(
            long id,
            int round,
            ProofSubmissionStatus status,
            Instant createdAt,
            Instant submittedAt,
            List<String> links,
            List<File> files) {
    }

    public record File(long id, String contentType, long sizeBytes, String downloadUrl) {
    }
}
