package com.flavors.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The follower offer of a campaign (table {@code offer}, V4), with the branches it is limited to ({@code offer_branch}).
 * One per campaign: the campaign_id column is unique. In the MVP an offer is a percentage discount (FR-F10.1).
 *
 * <p>The smallint columns are mapped as {@code short}: Hibernate checks the types at startup ({@code ddl-auto: validate})
 * and refuses an {@code int} for a smallint.
 */
@Entity
public class Offer {

    /** Every day of the week in {@link #validDays}: Monday = 1, Tuesday = 2, ... Sunday = 64 (V4). */
    public static final short EVERY_DAY = 127;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long campaignId;

    @Column(nullable = false)
    private short discountPercent;

    /** The first moment the offer no longer works: midnight at the end of the expiry day, in the restaurant's zone. */
    @Column(nullable = false)
    private Instant expiresAt;

    /** One bit per day (see {@link #EVERY_DAY}). */
    @Column(nullable = false)
    private short validDays = EVERY_DAY;

    /** Both empty: all day. Restaurant local time; hours past midnight are not supported in the MVP (V4). */
    private LocalTime validFrom;

    private LocalTime validTo;

    /** True: any of the brand's branches. False: only {@link #branchIds}. */
    @Column(nullable = false)
    private boolean allBranches = true;

    @ElementCollection
    @CollectionTable(name = "offer_branch", joinColumns = @JoinColumn(name = "offer_id"))
    @Column(name = "branch_id", nullable = false)
    private Set<Long> branchIds = new HashSet<>();

    @Column(nullable = false)
    private long minPurchaseCents;

    /** Empty: no maximum. */
    private Integer maxRedemptions;

    /** One unless the brand allows more (FR-F10.3). */
    @Column(nullable = false)
    private short perPersonLimit = 1;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected Offer() {
    }

    public Offer(Long campaignId) {
        this.campaignId = campaignId;
    }

    /** Replaces every condition. The caller has validated the values. */
    public void update(short discountPercent, Instant expiresAt, short validDays, LocalTime validFrom,
                       LocalTime validTo, boolean allBranches, Collection<Long> branchIds, long minPurchaseCents,
                       Integer maxRedemptions, short perPersonLimit, Instant now) {
        this.discountPercent = discountPercent;
        this.expiresAt = expiresAt;
        this.validDays = validDays;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.allBranches = allBranches;
        // A list of branches only means something when the offer is limited to them.
        this.branchIds.clear();
        if (!allBranches) {
            this.branchIds.addAll(branchIds);
        }
        this.minPurchaseCents = minPurchaseCents;
        this.maxRedemptions = maxRedemptions;
        this.perPersonLimit = perPersonLimit;
        this.updatedAt = now;
    }

    /** The {@link #validDays} bits for these days (Monday = 1 ... Sunday = 64). */
    public static short bitsOf(Collection<DayOfWeek> days) {
        int bits = 0;
        for (DayOfWeek day : days) {
            bits |= 1 << (day.getValue() - 1);
        }
        return (short) bits;
    }

    /** The days set in these bits, Monday first. */
    public static List<DayOfWeek> daysOf(short bits) {
        return Arrays.stream(DayOfWeek.values())
                .filter(day -> (bits & (1 << (day.getValue() - 1))) != 0)
                .toList();
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public short getDiscountPercent() {
        return discountPercent;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public short getValidDays() {
        return validDays;
    }

    public LocalTime getValidFrom() {
        return validFrom;
    }

    public LocalTime getValidTo() {
        return validTo;
    }

    public boolean isAllBranches() {
        return allBranches;
    }

    public Set<Long> getBranchIds() {
        return branchIds;
    }

    public long getMinPurchaseCents() {
        return minPurchaseCents;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public short getPerPersonLimit() {
        return perPersonLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
