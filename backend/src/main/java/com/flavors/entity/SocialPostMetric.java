package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * The figures of one post, as the last pull saw them (table {@code social_post_metric}, V2). One row per
 * post, overwritten by each pull (FR-F1.2), and summed per campaign by the ROI dashboards later.
 */
@Entity
public class SocialPostMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long socialAccountId;

    /** The post's id on the platform. */
    @Column(nullable = false)
    private String externalPostId;

    /** The campaign this post was made for; null for the creator's other posts. Set by the proof story. */
    private Long campaignId;

    @Column(nullable = false)
    private long views;

    @Column(nullable = false)
    private long likes;

    @Column(nullable = false)
    private long comments;

    @Column(nullable = false)
    private long shares;

    @Column(nullable = false)
    private Instant pulledAt;

    /** For JPA only. */
    protected SocialPostMetric() {
    }

    public SocialPostMetric(Long socialAccountId, String externalPostId) {
        this.socialAccountId = socialAccountId;
        this.externalPostId = externalPostId;
    }

    /** Replaces the figures with the ones from the latest pull. */
    public void record(long views, long likes, long comments, long shares, Instant pulledAt) {
        this.views = views;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
        this.pulledAt = pulledAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSocialAccountId() {
        return socialAccountId;
    }

    public String getExternalPostId() {
        return externalPostId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public long getViews() {
        return views;
    }

    public long getLikes() {
        return likes;
    }

    public long getComments() {
        return comments;
    }

    public long getShares() {
        return shares;
    }

    public Instant getPulledAt() {
        return pulledAt;
    }
}
