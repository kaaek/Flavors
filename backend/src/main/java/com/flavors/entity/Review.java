package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A brand's review of a creator after a completed campaign (table {@code review}, V3; UC-F1.4).
 * One review per campaign. Reviews can't be edited after submission.
 *
 * Reviews are created after an eligible campaign is completed and displayed
 * on the creator's portfolio.
 */
@Entity
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    /** 1 to 5 (a smallint in the table). */
    @Column(nullable = false)
    private short rating;

    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Review() {
    }
    public Review(Long campaignId, short rating, String text, Instant createdAt) {
        if (campaignId == null || campaignId <= 0) {
            throw new IllegalArgumentException("Invalid campaign ID");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time is required");
        }

        this.campaignId = campaignId;
        this.rating = rating;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public short getRating() {
        return rating;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
