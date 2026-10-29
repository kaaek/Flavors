package com.flavors.account;
 
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
 * <p>This class maps only the columns the portfolio reads. The encrypted OAuth token column
 * ({@code access_token_enc}) is left out on purpose: the connect story (EEC-33) adds it, and
 * nothing that shows a portfolio should ever be able to read it. The stories that connect an
 * account (EEC-33) or refresh its numbers (EEC-37) add their own methods here.
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