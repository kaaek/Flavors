package com.flavors.mapper;

import com.flavors.dto.DeliveryProofResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.ProofFile;
import com.flavors.entity.ProofLink;
import com.flavors.entity.ProofSubmission;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Converts a campaign's proof rows into the delivery-proof response (UC-F5.1). The service decides which
 * submissions the caller may see; this only converts them. A screenshot is exposed through its authenticated
 * download URL, never its storage key.
 */
@Component
public class DeliveryProofMapper {

    public DeliveryProofResponse toResponse(Campaign campaign, List<DeliveryProofResponse.Submission> submissions) {
        return new DeliveryProofResponse(campaign.getId(), campaign.getName(), campaign.getStatus(),
                campaign.getProofReviewDeadline(), campaign.isScreenshotsRequired(), submissions);
    }

    public DeliveryProofResponse.Submission toSubmission(ProofSubmission submission, List<ProofLink> links,
                                                         List<ProofFile> files) {
        return new DeliveryProofResponse.Submission(submission.getId(), submission.getRound(), submission.getStatus(),
                submission.getCreatedAt(), submission.getSubmittedAt(),
                links.stream().map(ProofLink::getUrl).toList(),
                files.stream().map(this::toFile).toList());
    }

    private DeliveryProofResponse.File toFile(ProofFile file) {
        return new DeliveryProofResponse.File(file.getId(), file.getContentType(), file.getSizeBytes(),
                "/api/proof-files/" + file.getId());
    }
}
