package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** One deliverable belonging to a campaign (table campaign_deliverable, V3). */
@Entity
public class CampaignDeliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false, length = 30)
    private String platform;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private Integer count;

    /** For JPA only. */
    protected CampaignDeliverable() {
    }

    public CampaignDeliverable(Long campaignId, String platform, String type, Integer count) {
        this.campaignId = campaignId;
        this.platform = platform;
        this.type = type;
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getType() {
        return type;
    }

    public Integer getCount() {
        return count;
    }
}
