package com.flavors.repository;

import com.flavors.entity.ProofReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProofReviewRepository extends JpaRepository<ProofReview, Long> {

    Optional<ProofReview> findBySubmissionId(Long submissionId);

    @Query("""
            select r from ProofReview r, ProofSubmission s
            where r.submissionId = s.id and s.campaignId = :campaignId
            order by s.round asc
            """)
    List<ProofReview> findByCampaignId(@Param("campaignId") Long campaignId);

    interface ApprovedReleaseCandidate {
        Long getCampaignId();
        Long getSubmissionId();
        Long getReviewId();
    }

    /** Keyset recovery of legacy approvals; the service rechecks eligibility under the campaign lock. */
    @Query("""
            select c.id as campaignId, s.id as submissionId, r.id as reviewId
            from Campaign c, EscrowPayment e, ProofSubmission s, ProofReview r
            where e.campaignId = c.id and s.campaignId = c.id and r.submissionId = s.id
              and r.id > :afterReviewId and r.decision = com.flavors.enums.ProofReviewDecision.APPROVED
              and r.decidedBy = c.brandId
              and c.status = com.flavors.enums.CampaignStatus.PROOF_SUBMITTED
              and e.status = com.flavors.enums.EscrowStatus.HELD
              and s.status = com.flavors.enums.ProofSubmissionStatus.SUBMITTED
              and s.round = c.correctionRoundsUsed + 1
              and not exists (select newer.id from ProofSubmission newer where newer.campaignId = c.id
                              and newer.status = com.flavors.enums.ProofSubmissionStatus.SUBMITTED
                              and newer.round > s.round)
              and not exists (select o.id from EscrowReleaseOperation o where o.escrowId = e.id)
            order by r.id
            """)
    List<ApprovedReleaseCandidate> findApprovedReleaseCandidates(@Param("afterReviewId") long afterReviewId,
                                                                Pageable pageable);
}
