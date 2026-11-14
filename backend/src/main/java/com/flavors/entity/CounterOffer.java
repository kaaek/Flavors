package com.flavors.entity;

import com.flavors.dto.DeliverableItem;
import com.flavors.enums.CounterOfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "counter_offer")
public class CounterOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long campaignId;

    @Column(nullable = false)
    private Long priceCents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<DeliverableItem> deliverables;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CounterOfferStatus status = CounterOfferStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant decidedAt;

    /** For JPA only. */
    protected CounterOffer() {
    }

    public CounterOffer(Long campaignId, Long priceCents,
                        List<DeliverableItem> deliverables) {
        this.campaignId = campaignId;
        this.priceCents = priceCents;
        this.deliverables = deliverables;
    }

    public void approve(Instant time) {
        ensurePending();
        status = CounterOfferStatus.APPROVED;
        decidedAt = time;
    }

    public void decline(Instant time) {
        ensurePending();
        status = CounterOfferStatus.DECLINED;
        decidedAt = time;
    }

    private void ensurePending() {
        if (status != CounterOfferStatus.PENDING) {
            throw new IllegalStateException("Only a pending counter-offer can be decided.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public List<DeliverableItem> getDeliverables() {
        return deliverables;
    }

    public CounterOfferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
