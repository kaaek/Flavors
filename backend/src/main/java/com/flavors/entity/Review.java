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
 * <p>This class only reads, for the creator's portfolio (UC-F1.1). The story that writes reviews
 * (EEC-38) adds the constructor and the rules for creating one here.
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
