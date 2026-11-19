package com.flavors.entity;

import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Durable payout state, not a transfer implementation. Later services must hold the campaign lock,
 * validate eligibility, and audit changes. Methods here never call a provider or mutate escrow.
 */
@Entity
public class EscrowReleaseOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long escrowId;

    @Column(nullable = false, updatable = false)
    private Long submissionId;

    @Column(updatable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReleaseAuthorizationSource authorizationSource;

    @Column(nullable = false, updatable = false)
    private long originalAmountCents;

    @Column(nullable = false, updatable = false)
    private long feeCents;

    @Column(nullable = false, updatable = false)
    private long creatorNetCents;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.AUTHORIZED;

    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String operationKey;

    @Column(length = 30)
    private String payoutProvider;

    @Column(length = 100)
    private String providerRef;

    @Column(length = 200)
    private String destinationRef;

    @Column(unique = true)
    private Long paymentTransactionId;

    @Column(nullable = false, updatable = false)
    private Instant authorizedAt;

    private Instant submittedAt;
    private Instant confirmedAt;
    private Instant lastAttemptAt;
    private Instant nextAttemptAt;
    private Instant needsReviewAt;

    @Column(nullable = false)
    private int dispatchAttempts;

    @Column(nullable = false)
    private int queryAttempts;

    @Column(nullable = false)
    private boolean retryable;

    @Column(length = 100)
    private String lastErrorCode;

    private UUID leaseToken;
    private Instant leaseUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected EscrowReleaseOperation() {
    }

    public EscrowReleaseOperation(long escrowId, long submissionId, Long reviewId,
                                 ReleaseAuthorizationSource source, long amountCents, Instant now) {
        if (escrowId <= 0 || submissionId <= 0 || amountCents <= 0
                || (reviewId != null && reviewId <= 0)) {
            throw new IllegalArgumentException("Release identifiers and amount must be positive.");
        }
        Objects.requireNonNull(source, "source");
        if ((source == ReleaseAuthorizationSource.APPROVAL) != (reviewId != null)) {
            throw new IllegalArgumentException("Only approval authorization must identify a review.");
        }
        this.escrowId = escrowId;
        this.submissionId = submissionId;
        this.reviewId = reviewId;
        this.authorizationSource = source;
        this.originalAmountCents = amountCents;
        Money amount = Money.ofCents(amountCents);
        Money fee = amount.percent(15);
        this.feeCents = fee.cents();
        this.creatorNetCents = amount.minus(fee).cents();
        this.operationKey = "escrow-release:" + escrowId;
        this.authorizedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** Persist UNKNOWN before dispatch: a crash after this point requires reconciliation, not a blind retry. */
    public void beginDispatch(String provider, String destination, long transactionId,
                              UUID token, Instant until, Instant now) {
        if (status != PayoutStatus.AUTHORIZED && !(status == PayoutStatus.FAILED && retryable)) {
            throw new IllegalStateException("Dispatch requires authorization or a definitive retryable failure.");
        }
        requireTime(now);
        requireText(provider, 30);
        requireText(destination, 200);
        if (transactionId <= 0 || token == null || until == null || !until.isAfter(now)) {
            throw new IllegalArgumentException("Dispatch requires a transaction and an unexpired lease.");
        }
        if (payoutProvider != null && (!payoutProvider.equals(provider) || !destinationRef.equals(destination)
                || !paymentTransactionId.equals(transactionId))) {
            throw new IllegalStateException("The payout provider, destination, and transaction are pinned.");
        }
        payoutProvider = provider;
        destinationRef = destination;
        paymentTransactionId = transactionId;
        leaseToken = token;
        leaseUntil = until;
        lastAttemptAt = now;
        dispatchAttempts = Math.incrementExact(dispatchAttempts);
        status = PayoutStatus.UNKNOWN;
        retryable = false;
        nextAttemptAt = null;
        lastErrorCode = null;
        updatedAt = now;
    }

    /** A status query is metadata only; it must never authorize a new dispatch. */
    public void recordQuery(Instant now) {
        requirePending();
        requireTime(now);
        queryAttempts = Math.incrementExact(queryAttempts);
        lastAttemptAt = now;
        // A worker must acquire its own lease before network I/O; don't retain an older dispatch lease.
        clearLease();
        updatedAt = now;
    }

    /** Acquire a query lease without ever turning UNKNOWN back into dispatchable authorization. */

    /**
     * Acquire a lease for reconciliation.
     * Do not increment queryAttempts until the provider call is attempted.
     */
    public void beginQuery(UUID token, Instant until, Instant now) {
        requirePending();
        requireTime(now);

        if (token == null || until == null || !until.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Reconciliation requires an unexpired lease.");
        }

        leaseToken = token;
        leaseUntil = until;
        updatedAt = now;
    }


    public void scheduleNextAttempt(Instant next) {
        if (status == PayoutStatus.CONFIRMED || next == null || next.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Only unfinished operations can schedule a future attempt.");
        }
        nextAttemptAt = next;
    }


    /**
     * Mark an unresolved payout for manual review.
     * Preserve the original escalation timestamp.
     */
    public void markNeedsReview(Instant now) {
        requireTime(now);

        if (status != PayoutStatus.UNKNOWN
                && status != PayoutStatus.SUBMITTED
                && !(status == PayoutStatus.FAILED && !retryable)) {
            throw new IllegalStateException(
                    "Only unresolved payouts can require manual review.");
        }

        if (needsReviewAt == null) {
            needsReviewAt = now;
            updatedAt = now;
        }
    }



    public void recordSubmitted(String reference, Instant now) {
        requirePending();
        requireTime(now);
        pinReference(reference);
        if (submittedAt == null) {
            submittedAt = now;
        }
        status = PayoutStatus.SUBMITTED;
        retryable = false;
        lastErrorCode = null;
        nextAttemptAt = null;
        clearLease();
        updatedAt = now;
    }

    public void recordUnknown(String errorCode, Instant nextCheck, Instant now) {
        requirePending();
        requireTime(now);
        requireText(errorCode, 100);
        status = PayoutStatus.UNKNOWN;
        lastErrorCode = errorCode;
        nextAttemptAt = nextCheck;
        retryable = false;
        clearLease();
        updatedAt = now;
    }

    /** Only a definitive provider failure belongs here. Timeout/ambiguous answers use recordUnknown. */
    public void recordFailed(String errorCode, boolean mayRetry, Instant nextAttempt, Instant now) {
        requirePending();
        requireTime(now);
        requireText(errorCode, 100);
        status = PayoutStatus.FAILED;
        lastErrorCode = errorCode;
        retryable = mayRetry;
        nextAttemptAt = nextAttempt;
        clearLease();
        updatedAt = now;
    }

    /** Records an authoritative confirmation supplied by a future service; it does not release escrow itself. */
    public void confirm(String reference, Instant now) {
        requireTime(now);
        if (status == PayoutStatus.CONFIRMED) {
            if (!Objects.equals(providerRef, reference)) {
                throw new IllegalStateException("Conflicting confirmation reference.");
            }
            return;
        }
        if (status == PayoutStatus.AUTHORIZED) {
            throw new IllegalStateException("An undispatched authorization is not a confirmed payout.");
        }
        pinReference(reference);
        if (submittedAt == null) {
            submittedAt = now;
        }
        confirmedAt = now;
        status = PayoutStatus.CONFIRMED;
        needsReviewAt = null;
        retryable = false;
        nextAttemptAt = null;
        lastErrorCode = null;
        clearLease();
        updatedAt = now;
    }

    private void requirePending() {
        if (status != PayoutStatus.UNKNOWN && status != PayoutStatus.SUBMITTED) {
            throw new IllegalStateException("The payout has no pending provider outcome.");
        }
    }

    private void requireTime(Instant now) {
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Payout timestamps cannot move backwards.");
        }
    }

    private static void requireText(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("A nonblank value of at most " + max + " characters is required.");
        }
    }

    private void pinReference(String reference) {
        requireText(reference, 100);
        if (providerRef != null && !providerRef.equals(reference)) {
            throw new IllegalStateException("The provider reference is pinned.");
        }
        providerRef = reference;
    }

    private void clearLease() {
        leaseToken = null;
        leaseUntil = null;
    }

    public Long getId() {
        return id;
    }

    public Long getEscrowId() {
        return escrowId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public ReleaseAuthorizationSource getAuthorizationSource() {
        return authorizationSource;
    }

    public long getOriginalAmountCents() {
        return originalAmountCents;
    }

    public long getFeeCents() {
        return feeCents;
    }

    public long getCreatorNetCents() {
        return creatorNetCents;
    }

    public String getCurrency() {
        return currency;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public String getPayoutProvider() {
        return payoutProvider;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getDestinationRef() {
        return destinationRef;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getDispatchAttempts() {
        return dispatchAttempts;
    }

    public int getQueryAttempts() {
        return queryAttempts;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public UUID getLeaseToken() {
        return leaseToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public Instant getNeedsReviewAt() {
        return needsReviewAt;
    }
}
