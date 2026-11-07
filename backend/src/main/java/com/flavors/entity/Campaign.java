package com.flavors.entity;

import com.flavors.enums.CampaignStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A campaign (table {@code campaign}, V3).
 *
 * <p>This class maps the columns the portfolio and delivery-proof stories use. The campaign stories (brand creates
 * and sends a campaign, creator responds, and so on) add the other columns and the methods
 * that change a campaign here.
 */
@Entity
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long brandId;

    /** Empty while the campaign is a draft. */
    private Long creatorId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    /** What is actually paid to the creator; set when she accepts. */
    private Long agreedPriceCents;

    /** Timer C: five days after the latest final proof submission. */
    private Instant proofReviewDeadline;

    @Column(nullable = false)
    private short correctionRoundsUsed;

    @Column(nullable = false, insertable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected Campaign() {
    }

    public Long getId() {
        return id;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public String getName() {
        return name;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Long getAgreedPriceCents() {
        return agreedPriceCents;
    }

    /** The first version is round 1; each accepted correction request opens one later round. */
    public int currentProofRound() {
        return correctionRoundsUsed + 1;
    }

    public void markProofSubmitted(Instant submittedAt, Instant reviewDeadline) {
        status = CampaignStatus.PROOF_SUBMITTED;
        proofReviewDeadline = reviewDeadline;
        updatedAt = submittedAt;
    }

    public Instant getProofReviewDeadline() {
        return proofReviewDeadline;
    }

    public int getCorrectionRoundsUsed() {
        return correctionRoundsUsed;
    }
}
