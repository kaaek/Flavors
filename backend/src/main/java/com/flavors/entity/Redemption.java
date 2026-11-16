package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A diner's redemption at the till (table {@code redemption}, V4).
 *
 * <p>This class maps only the columns the portfolio counts and sums. The redemption stories
 * (the scan, the dashboards) add the other columns and the methods that create one.
 */
@Entity
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    /** The bill total before the discount, as the cashier typed it. */
    @Column(nullable = false)
    private long billCents;

    @Column(nullable = false)
    private long discountCents;

    /** For JPA only. */
    protected Redemption() {
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public long getBillCents() {
        return billCents;
    }

    public long getDiscountCents() {
        return discountCents;
    }
}
