package com.flavors.entity;

import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.enums.PaymentTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One money movement with the provider, failed attempts included (table {@code payment_transaction}, V5). For a
 * payment, {@link #providerRef} is the reference the provider knows the attempt by: it is unique, so the same
 * confirmation can match one row only (EX-F7.1.3).
 */
@Entity
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long escrowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionStatus status;

    @Column(nullable = false)
    private Long amountCents;

    @Column(unique = true, length = 100)
    private String providerRef;

    @Column(length = 200)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected PaymentTransaction() {
    }

    public PaymentTransaction(Long escrowId, PaymentTransactionType type, long amountCents, String providerRef,
                              String idempotencyKey) {
        this.escrowId = escrowId;
        this.type = type;
        this.status = PaymentTransactionStatus.PENDING;
        this.amountCents = amountCents;
        this.providerRef = providerRef;
        this.idempotencyKey = idempotencyKey;
    }

    public void succeed() {
        this.status = PaymentTransactionStatus.SUCCEEDED;
    }

    public void fail() {
        this.status = PaymentTransactionStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public Long getEscrowId() {
        return escrowId;
    }

    public PaymentTransactionType getType() {
        return type;
    }

    public PaymentTransactionStatus getStatus() {
        return status;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
