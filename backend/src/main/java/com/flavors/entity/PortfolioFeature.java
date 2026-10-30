package com.flavors.entity;
 
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
 
/**
 * A completed campaign a creator chose to feature on her portfolio (table
 * {@code portfolio_feature}, V6; UC-F1.2, FR-F1.6). A campaign has one creator, so the
 * campaign id alone identifies the row.
 *
 * <p>The rules that span tables (the campaign must be COMPLETED, and must be this creator's)
 * are checked by the portfolio service, not here.
 */
@Entity
public class PortfolioFeature {
 
    @Id
    private Long campaignId;
 
    @Column(nullable = false)
    private Long creatorId;
 
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
 
    /** For JPA only. */
    protected PortfolioFeature() {
    }
 
    public PortfolioFeature(Long campaignId, Long creatorId) {
        this.campaignId = campaignId;
        this.creatorId = creatorId;
    }
 
    public Long getCampaignId() {
        return campaignId;
    }
 
    public Long getCreatorId() {
        return creatorId;
    }
 
    public Instant getCreatedAt() {
        return createdAt;
    }
}