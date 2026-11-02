package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * A completed campaign a creator chose to feature on her portfolio (table
 * {@code portfolio_feature}, V6; UC-F1.2, FR-F1.6). A campaign has one creator, so the
 * campaign id alone identifies the row.
 *
 * <p>The rules that span tables (the campaign must be COMPLETED, and must be this creator's)
 * are checked by the portfolio service, not here.
 *
 * <p>The id is assigned by the caller, so the class says by itself that a new object is new
 * ({@link Persistable}). Without that, Spring Data would first SELECT the row, and a second
 * insert of the same campaign could be mistaken for an update instead of failing on the primary key.
 */
@Entity
public class PortfolioFeature implements Persistable<Long> {

    @Id
    private Long campaignId;

    @Column(nullable = false)
    private Long creatorId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** True until the row is saved or loaded; not a column. */
    @Transient
    private boolean newRow = true;

    /** For JPA only. */
    protected PortfolioFeature() {
    }

    public PortfolioFeature(Long campaignId, Long creatorId) {
        this.campaignId = campaignId;
        this.creatorId = creatorId;
    }

    @Override
    public Long getId() {
        return campaignId;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newRow = false;
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