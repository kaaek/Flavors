package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An in-app notification (table {@code notification}, V5). This class only writes them for now; the
 * notification story (UC-F18.1) adds reading and marking them as read.
 */
@Entity
public class Notification {

    /** A brand sent the creator a campaign request (UC-F3.1). */
    public static final String CAMPAIGN_REQUEST = "CAMPAIGN_REQUEST";
    public static final String CAMPAIGN_ACCEPTED = "CAMPAIGN_ACCEPTED";
    public static final String CAMPAIGN_REJECTED = "CAMPAIGN_REJECTED";
    public static final String COUNTER_OFFER_RECEIVED = "COUNTER_OFFER_RECEIVED";
    public static final String COUNTER_OFFER_APPROVED = "COUNTER_OFFER_APPROVED";
    public static final String COUNTER_OFFER_DECLINED = "COUNTER_OFFER_DECLINED";
    public static final String CAMPAIGN_RESPONSE_EXPIRED = "CAMPAIGN_RESPONSE_EXPIRED";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** The campaign to open from the notification. */
    private Long campaignId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String message;

    /** Empty while unread. */
    private Instant readAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Notification() {
    }

    public Notification(Long userId, Long campaignId, String type, String message) {
        this.userId = userId;
        this.campaignId = campaignId;
        this.type = type;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
