package com.flavors.entity;

import com.flavors.enums.ProofSubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** One draft or final delivery-proof version for a campaign round (V5). */
@Entity
public class ProofSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private short round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProofSubmissionStatus status = ProofSubmissionStatus.DRAFT;

    private Instant submittedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected ProofSubmission() {
    }

    public ProofSubmission(long campaignId, int round) {
        if (round < 1 || round > 3) {
            throw new IllegalArgumentException("Proof round must be between 1 and 3.");
        }
        this.campaignId = campaignId;
        this.round = (short) round;
    }

    public void submit(Instant now) {
        if (status != ProofSubmissionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft can be submitted.");
        }
        status = ProofSubmissionStatus.SUBMITTED;
        submittedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public int getRound() {
        return round;
    }

    public ProofSubmissionStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
