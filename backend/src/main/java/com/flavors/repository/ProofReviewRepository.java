package com.flavors.repository;

import com.flavors.entity.ProofReview;
import java.util.List;
import java.util.Optional;
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
}
