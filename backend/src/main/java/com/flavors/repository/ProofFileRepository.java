package com.flavors.repository;

import com.flavors.entity.ProofFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofFileRepository extends JpaRepository<ProofFile, Long> {

    List<ProofFile> findBySubmissionIdOrderById(Long submissionId);
}
