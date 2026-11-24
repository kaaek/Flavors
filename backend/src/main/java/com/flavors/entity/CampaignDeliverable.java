package com.flavors.entity;

import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** One thing the creator has to publish, e.g. 2 x Instagram reel (table {@code campaign_deliverable}, V3). */
@Entity
public class CampaignDeliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliverablePlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliverableType type;

    @Column(nullable = false)
    private int count;

    /** For JPA only. */
    protected CampaignDeliverable() {
    }

    public CampaignDeliverable(DeliverablePlatform platform, DeliverableType type, int count) {
        this.platform = platform;
        this.type = type;
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public DeliverablePlatform getPlatform() {
        return platform;
    }

    public DeliverableType getType() {
        return type;
    }

    public int getCount() {
        return count;
    }
}
