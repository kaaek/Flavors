package com.flavors.repository;

import com.flavors.entity.ProofSubmission;
import com.flavors.enums.ProofSubmissionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofSubmissionRepository extends JpaRepository<ProofSubmission, Long> {

    List<ProofSubmission> findByCampaignIdOrderByRoundAsc(Long campaignId);

    Optional<ProofSubmission> findByCampaignIdAndStatus(Long campaignId, ProofSubmissionStatus status);
}
