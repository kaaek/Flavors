package com.flavors.service;

import com.flavors.dto.CampaignProofReviewsResponse;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.dto.ProofReviewResponse;
import com.flavors.enums.Role;
import java.util.List;

/** Brand review core (EEC-47); settlement and dispute creation remain separate stories. */
public interface ProofReviewService {

    ProofReviewResponse review(long brandId, long campaignId, long submissionId, ProofReviewRequest request);

    CampaignProofReviewsResponse history(long userId, Role role, long campaignId);

    /** Bounded candidates for UC-F7.2's future Timer C worker; this method never moves money. */
    List<Long> findOverdueCampaignIds();
}
