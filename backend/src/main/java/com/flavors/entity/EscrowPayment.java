package com.flavors.entity;

import com.flavors.enums.EscrowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The brand's payment for one campaign, held by Flavors (table {@code escrow_payment}, V5). EEC-44 creates it
 * when the brand starts paying and moves it to HELD when the provider confirms the money. Release and refund
 * are EEC-45.
 */
@Entity
public class EscrowPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscrowStatus status;

    /** The campaign's agreed price in USD cents. */
    @Column(nullable = false)
    private Long amountCents;

    @Column(nullable = false)
    private Long feeCents = 0L;

    @Column(nullable = false)
    private Long creatorNetCents = 0L;

    @Column(nullable = false)
    private Long refundedCents = 0L;

    /** The payment provider the money went through, e.g. WHISH. */
    @Column(nullable = false, length = 30)
    private String provider;

    /** The reference of the latest payment attempt at the provider (unique, V5). */
    @Column(length = 100)
    private String providerPaymentId;

    private Instant heldAt;

    private Instant releasedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected EscrowPayment() {
    }

    public EscrowPayment(Long campaignId, long amountCents, String provider, Instant now) {
        this.campaignId = campaignId;
        this.status = EscrowStatus.PENDING;
        this.amountCents = amountCents;
        this.provider = provider;
        this.updatedAt = now;
    }

    /** Records the reference of the attempt that is now the one the brand pays through. */
    public void attachProviderPayment(String reference, Instant now) {
        this.providerPaymentId = reference;
        this.updatedAt = now;
    }

    /** The provider confirmed the money: the escrow is HELD (FR-F7.3). */
    public void hold(Instant now) {
        if (status != EscrowStatus.PENDING) {
            throw new IllegalStateException("Escrow " + id + " is " + status + ", not PENDING");
        }
        this.status = EscrowStatus.HELD;
        this.heldAt = now;
        this.updatedAt = now;
    }

    /** Freezing reserves the held balance for later dispute handling; no money moves. */
    public void freeze(Instant now) {
        if (status != EscrowStatus.HELD) {
            throw new IllegalStateException("Only held escrow can be frozen.");
        }
        updatedAt = Objects.requireNonNull(now);
        status = EscrowStatus.FROZEN;
    }

    /** Called only with an authoritative payout confirmation and the stored fee/net snapshot. */
    public void release(long fee, long net, Instant now) {
        if (status != EscrowStatus.HELD || fee < 0 || net < 0 || Math.addExact(fee, net) != getAmountCents()
                || refundedCents != 0) {
            throw new IllegalStateException("Release requires the complete held balance.");
        }
        releasedAt = Objects.requireNonNull(now);
        updatedAt = now;
        feeCents = fee;
        creatorNetCents = net;
        status = EscrowStatus.RELEASED;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public EscrowStatus getStatus() {
        return status;
    }

    public long getAmountCents() {
        return Objects.requireNonNull(amountCents);
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public Instant getHeldAt() {
        return heldAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
