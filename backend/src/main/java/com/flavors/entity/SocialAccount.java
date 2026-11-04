package com.flavors.entity;

import com.flavors.enums.SocialAccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A social account a creator connected (table {@code social_account}, V2). The follower and
 * engagement figures come from the platform, never from the creator.
 *
 * <p>The encrypted OAuth token column ({@code access_token_enc}) is deliberately not mapped here, so
 * nothing that loads this entity (the portfolio, the admin review) can ever read it. EEC-33 writes and
 * reads it only through {@code SocialAccountTokenRepository}.
 */
@Entity
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The creator's user id (creator_profile.user_id). */
    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private String platform;

    /** The account's id on the platform. */
    @Column(nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialAccountStatus status = SocialAccountStatus.CONNECTED;

    private Long followers;

    /** Percentage, e.g. 4.25 means 4.25 %. */
    @Column(precision = 7, scale = 2)
    private BigDecimal engagementRate;

    /** Shown as 'last updated'; a failed pull keeps the old time. */
    private Instant lastPulledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected SocialAccount() {
    }

    public SocialAccount(Long creatorId, String platform, String externalId) {
        this.creatorId = creatorId;
        this.platform = platform;
        this.externalId = externalId;
    }

    /**
     * Stores the figures of a successful pull, with the time of the pull, and marks the account
     * connected again (FR-F19.15, FR-F1.2).
     */
    public void recordPull(long followers, BigDecimal engagementRate, Instant pulledAt) {
        this.followers = followers;
        this.engagementRate = engagementRate;
        this.lastPulledAt = pulledAt;
        this.status = SocialAccountStatus.CONNECTED;
    }

    /**
     * After a disconnect or a failed pull: the last figures and their 'last updated' time stay, and the
     * creator sees 'Needs reconnection' (FR-F19.16, FR-F1.9).
     */
    public void markNeedsReconnection() {
        this.status = SocialAccountStatus.NEEDS_RECONNECTION;
    }

    public Long getId() {
        return id;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getExternalId() {
        return externalId;
    }

    public SocialAccountStatus getStatus() {
        return status;
    }

    public Long getFollowers() {
        return followers;
    }

    public BigDecimal getEngagementRate() {
        return engagementRate;
    }

    public Instant getLastPulledAt() {
        return lastPulledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
