package com.flavors.repository;

import com.flavors.entity.ProofLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofLinkRepository extends JpaRepository<ProofLink, Long> {

    List<ProofLink> findBySubmissionIdOrderById(Long submissionId);
}
