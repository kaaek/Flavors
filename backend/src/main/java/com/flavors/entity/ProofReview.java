package com.flavors.entity;

import com.flavors.enums.ProofReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** One immutable brand decision per submitted version; the unique constraint is already in V5. */
@Entity
public class ProofReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private ProofReviewDecision decision;

    @Column(updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Long decidedBy;

    @Column(nullable = false, updatable = false)
    private Instant decidedAt;

    protected ProofReview() {
    }

    public ProofReview(long submissionId, ProofReviewDecision decision, String reason, long decidedBy,
                       Instant decidedAt) {
        this.submissionId = submissionId;
        this.decision = decision;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public ProofReviewDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
