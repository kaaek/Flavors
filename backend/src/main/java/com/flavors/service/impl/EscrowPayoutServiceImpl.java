package com.flavors.service.impl;

import com.flavors.dto.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;
import com.flavors.entity.Campaign;
import com.flavors.entity.EscrowPayment;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.entity.PaymentTransaction;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.enums.PaymentTransactionType;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.EscrowPaymentRepository;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.repository.PaymentTransactionRepository;
import com.flavors.repository.ProofReviewRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.EscrowPayoutService;
import com.flavors.service.PayoutProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EscrowPayoutServiceImpl implements EscrowPayoutService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EscrowPayoutServiceImpl.class);
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final int REVIEW_THRESHOLD = 10;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private final CampaignRepository campaigns;
    private final EscrowPaymentRepository escrows;
    private final EscrowReleaseOperationRepository operations;
    private final PaymentTransactionRepository transactions;
    private final ProofSubmissionRepository submissions;
    private final ProofReviewRepository reviews;
    private final AuditLogger audit;
    private final PayoutProvider provider;
    private final TransactionOperations tx;
    private final Clock clock;

    @Autowired
    public EscrowPayoutServiceImpl(CampaignRepository campaigns, EscrowPaymentRepository escrows,
            EscrowReleaseOperationRepository operations, PaymentTransactionRepository transactions,
            ProofSubmissionRepository submissions, ProofReviewRepository reviews, AuditLogger audit,
            PayoutProvider provider, TransactionOperations tx) {
        this(campaigns, escrows, operations, transactions, submissions, reviews, audit, provider,
                tx, Clock.systemUTC());
    }

    EscrowPayoutServiceImpl(CampaignRepository campaigns, EscrowPaymentRepository escrows,
            EscrowReleaseOperationRepository operations, PaymentTransactionRepository transactions,
            ProofSubmissionRepository submissions, ProofReviewRepository reviews, AuditLogger audit,
            PayoutProvider provider, TransactionOperations tx, Clock clock) {
        this.campaigns = campaigns; this.escrows = escrows; this.operations = operations;
        this.transactions = transactions; this.submissions = submissions; this.reviews = reviews;
        this.audit = audit; this.provider = provider; this.tx = tx; this.clock = clock;
    }

    /** Records a payout claim prepared under a database lock.
 * The lease must be verified before contacting the provider.
 */
    public record Preparation(long campaignId, long escrowId, long operationId, UUID leaseToken,
                            boolean dispatch, PayoutRequest request, String providerRef) {}
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int processPendingPayouts() {
        if (!provider.available()) return 0;
        int processed = 0;
        for (long id : operations.findDuePayoutIds(now(), PageRequest.of(0, 100))) {
            try {
                if (processOperation(id)) processed++;
            } catch (RuntimeException e) {
                LOGGER.warn("Payout processing failed for operation {} ({}); durable state retained.",
                        id, e.getClass().getSimpleName());
            }
        }
        return processed;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean processOperation(long operationId) {
        if (!provider.available()) return false;
        var snapshot = operations.findById(operationId).orElse(null);
        if (snapshot == null || snapshot.getStatus() == PayoutStatus.CONFIRMED) return false;
        var escrow = escrows.findById(snapshot.getEscrowId()).orElseThrow();
        UUID token = UUID.randomUUID();
        // Database locks and lease token prevent duplicate preparation.
        // The provider uses the stable operation key.
        Preparation claim = tx.execute(s ->prepare(escrow.getCampaignId(), snapshot.getEscrowId(), operationId, token));
        if (claim == null) return false;
        // Never replay a cached preparation as an external action. Verify its live lease after commit.
        if (!Boolean.TRUE.equals(tx.execute(s -> ownsClaim(claim)))) return false;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Payout provider calls must be outside database transactions.");
        }
        PayoutResult result;
        try {
            result = claim.dispatch() ? provider.submit(claim.request())
                    : provider.query(claim.request(), claim.providerRef());
        } catch (RuntimeException e) {
            result = PayoutResult.unknown(claim.request()); // Timeouts/errors cannot prove a transfer failed.
        }
        PayoutResult received = result;
        boolean applied = Boolean.TRUE.equals(tx.execute(s -> apply(claim, received)));
        return applied;
        }

    private Preparation prepare(long campaignId, long escrowId, long operationId, UUID token) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId).orElseThrow();
        EscrowReleaseOperation operation = operations.findByEscrowIdForUpdate(escrowId).orElseThrow();
        EscrowPayment escrow = escrows.findById(escrowId).orElseThrow();
        Instant now = now();
        if (!Objects.equals(operation.getId(), operationId) || !eligible(campaign, escrow, operation)
                || operation.getStatus() == PayoutStatus.CONFIRMED
                || (operation.getLeaseUntil() != null && operation.getLeaseUntil().isAfter(now))
                || (operation.getNextAttemptAt() != null && operation.getNextAttemptAt().isAfter(now))
                || (operation.getPayoutProvider() != null && !operation.getPayoutProvider().equals(provider.name()))) {
            return null;
        }
        boolean dispatch = operation.getStatus() == PayoutStatus.AUTHORIZED
                || (operation.getStatus() == PayoutStatus.FAILED && operation.isRetryable());
        if (!dispatch && operation.getStatus() != PayoutStatus.UNKNOWN && operation.getStatus() != PayoutStatus.SUBMITTED) {
            return null;
        }
        // No real beneficiary onboarding exists. Only the simulator may obtain a synthetic destination.
        if (!provider.simulated()) return null;
        String destination = "simulated-creator:" + campaign.getCreatorId();
        if (operation.getDestinationRef() != null && !operation.getDestinationRef().equals(destination)) return null;
        PaymentTransaction payment;
        if (operation.getPaymentTransactionId() == null) {
            payment = transactions.saveAndFlush(new PaymentTransaction(escrowId, PaymentTransactionType.RELEASE,
                    operation.getCreatorNetCents(), null, operation.getOperationKey()));
        } else {
            payment = transactions.findById(operation.getPaymentTransactionId()).orElseThrow();
            validatePayment(payment, operation);
            if (dispatch && payment.getStatus() == PaymentTransactionStatus.FAILED) payment.retryPayout();
        }
        if (dispatch) {
            operation.beginDispatch(provider.name(), destination, payment.getId(), token, now.plus(LEASE), now);
        } else {
            operation.beginQuery(token, now.plus(LEASE), now);
        }
        audit.record(dispatch ? "PAYOUT_DISPATCH_PREPARED" : "PAYOUT_RECONCILIATION_STARTED",
                "escrow_release_operation", operationId, details(campaignId, operation));
        return new Preparation(campaignId, escrowId, operationId, token, dispatch,
                new PayoutRequest(operation.getOperationKey(), operation.getDestinationRef(),
                        operation.getCreatorNetCents(), operation.getCurrency()), operation.getProviderRef());
    }

    private boolean ownsClaim(Preparation claim) {
        Campaign campaign = campaigns.findByIdForUpdate(claim.campaignId()).orElseThrow();
        var operation = operations.findByEscrowIdForUpdate(claim.escrowId()).orElseThrow();
        var escrow = escrows.findById(claim.escrowId()).orElseThrow();
        return matchesClaim(operation, claim) && operation.getLeaseUntil().isAfter(now())
                && eligible(campaign, escrow, operation);
    }

    private boolean apply(Preparation claim, PayoutResult result) {
        Campaign campaign = campaigns.findByIdForUpdate(claim.campaignId()).orElseThrow();
        var operation = operations.findByEscrowIdForUpdate(claim.escrowId()).orElseThrow();
        var escrow = escrows.findById(claim.escrowId()).orElseThrow();
        if (!matchesClaim(operation, claim) || !eligible(campaign, escrow, operation)) return false;
        Instant now = now();
        var payment = transactions.findById(operation.getPaymentTransactionId()).orElseThrow();
        validatePayment(payment, operation);
        if (result == null || !claim.request().equals(result.identity())) result = PayoutResult.unknown(claim.request());
        // Count reconciliation calls after the provider invocation,
        // not when the database lease was prepared.
        if (!claim.dispatch()) {
            operation.recordQuery(now);
        }
        switch (result.outcome()) {
            case CONFIRMED -> {
                operation.confirm(result.providerRef(), now);
                payment.recordPayoutReference(result.providerRef());
                payment.succeed();
                escrow.release(operation.getFeeCents(), operation.getCreatorNetCents(), now);
                campaign.completeAfterPayout(now);
                audit.record("PAYOUT_CONFIRMED", "escrow_release_operation", operation.getId(), details(campaign.getId(), operation));
                audit.record("ESCROW_RELEASED", "escrow_payment", escrow.getId(), details(campaign.getId(), operation));
                audit.record("CAMPAIGN_COMPLETED", "campaign", campaign.getId(), details(campaign.getId(), operation));
            }
            case PENDING -> {
                operation.recordSubmitted(result.providerRef(), now);
                payment.recordPayoutReference(result.providerRef());
                operation.scheduleNextAttempt(now.plus(reconciliationBackoff(operation.getQueryAttempts())));
            }
            case UNKNOWN -> operation.recordUnknown("PROVIDER_OUTCOME_UNKNOWN",now.plus(reconciliationBackoff(operation.getQueryAttempts())),now);
            case NOT_FOUND -> {
                if (!claim.dispatch() && operation.getProviderRef() == null) {
                    // The provider confirmed no transfer exists.
                    // Allow retry using the original operation key.
                    operation.recordFailed(
                            "PROVIDER_NOT_FOUND",
                            true,
                            now.plus(Duration.ofMinutes(1)),
                            now);

                    payment.fail();

                    audit.record(
                            "PAYOUT_NOT_FOUND_RETRY_SCHEDULED",
                            "escrow_release_operation",
                            operation.getId(),
                            details(campaign.getId(), operation));
                } else {
                    // A payout with an existing provider reference
                    // must not automatically be dispatched again.
                    operation.recordUnknown(
                            "PROVIDER_NOT_FOUND_UNEXPECTED",
                            now.plus(reconciliationBackoff(
                                    operation.getQueryAttempts())),
                            now);

                    operation.markNeedsReview(now);

                    audit.record(
                            "PAYOUT_NEEDS_REVIEW",
                            "escrow_release_operation",
                            operation.getId(),
                            details(campaign.getId(), operation));
                }
            }
            case FAILED -> {
                operation.recordFailed("PROVIDER_DEFINITIVE_FAILURE", result.safeToRetry(),
                        result.safeToRetry() ? now.plus(Duration.ofMinutes(1)) : null, now);
                payment.fail();
            }
        }

        if (operation.getStatus() == PayoutStatus.FAILED
                && !operation.isRetryable()
                && operation.getNeedsReviewAt() == null) {

            operation.markNeedsReview(now);

            audit.record(
                    "PAYOUT_NEEDS_REVIEW",
                    "escrow_release_operation",
                    operation.getId(),
                    details(campaign.getId(), operation));

            LOGGER.warn(
                    "Payout operation {} requires manual review: definitive failure.",
                    operation.getId());
        }


        if ((operation.getStatus() == PayoutStatus.UNKNOWN
                || operation.getStatus() == PayoutStatus.SUBMITTED)
                && operation.getQueryAttempts() >= REVIEW_THRESHOLD
                && operation.getNeedsReviewAt() == null) {

            operation.markNeedsReview(now);

            audit.record(
                    "PAYOUT_NEEDS_REVIEW",
                    "escrow_release_operation",
                    operation.getId(),
                    details(campaign.getId(), operation));

            LOGGER.warn(
                    "Payout operation {} requires manual review after {} reconciliation attempts.",
                    operation.getId(),
                    operation.getQueryAttempts());
        }

        if (operation.getStatus() != PayoutStatus.CONFIRMED) {
            audit.record("PAYOUT_" + operation.getStatus().name(), "escrow_release_operation", operation.getId(),
                    details(campaign.getId(), operation));
        }
        return true;
    }

    private boolean eligible(Campaign campaign, EscrowPayment escrow, EscrowReleaseOperation operation) {
        if (campaign.getStatus() != CampaignStatus.PROOF_SUBMITTED || escrow.getStatus() != EscrowStatus.HELD
                || escrow.getAmountCents() != operation.getOriginalAmountCents()
                || !Objects.equals(campaign.getAgreedPriceCents(), operation.getOriginalAmountCents())) return false;
        var latest = submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(campaign.getId(), ProofSubmissionStatus.SUBMITTED)
                .orElse(null);
        if (latest == null || !Objects.equals(latest.getId(), operation.getSubmissionId())
                || latest.getRound() != campaign.currentProofRound()
                || reviews.findByCampaignId(campaign.getId()).stream().anyMatch(r -> r.getDecision() == ProofReviewDecision.REJECTED)) {
            return false;
        }
        var review = reviews.findBySubmissionId(latest.getId());
        return operation.getAuthorizationSource() == ReleaseAuthorizationSource.TIMER_C ? review.isEmpty()
                : review.filter(r -> Objects.equals(r.getId(), operation.getReviewId())
                        && r.getDecision() == ProofReviewDecision.APPROVED
                        && Objects.equals(r.getDecidedBy(), campaign.getBrandId())).isPresent();
    }

    private boolean matchesClaim(EscrowReleaseOperation operation, Preparation claim) {
        return Objects.equals(operation.getId(), claim.operationId())
                && Objects.equals(operation.getLeaseToken(), claim.leaseToken())
                && provider.name().equals(operation.getPayoutProvider())
                && claim.request().equals(new PayoutRequest(operation.getOperationKey(), operation.getDestinationRef(),
                        operation.getCreatorNetCents(), operation.getCurrency()));
    }

    private void validatePayment(PaymentTransaction payment, EscrowReleaseOperation operation) {
        if (!Objects.equals(payment.getEscrowId(), operation.getEscrowId()) || payment.getType() != PaymentTransactionType.RELEASE
                || payment.getAmountCents() != operation.getCreatorNetCents()
                || payment.getStatus() == PaymentTransactionStatus.SUCCEEDED) {
            throw new IllegalStateException("Payout transaction identity mismatch.");
        }
    }

    private Map<String, Object> details(long campaignId, EscrowReleaseOperation operation) {
        return Map.of("campaignId", campaignId, "operationId", operation.getId(), "escrowId", operation.getEscrowId(),
                "amountCents", operation.getOriginalAmountCents(), "feeCents", operation.getFeeCents(),
                "creatorNetCents", operation.getCreatorNetCents(), "simulated", provider.simulated());
    }

    private Duration reconciliationBackoff(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 6);
        long minutes = Math.min(1L << exponent, MAX_BACKOFF.toMinutes());
        return Duration.ofMinutes(minutes);
    }

    /**
     * Records administrator evidence for an escalated payout.
     * Does not confirm a transfer, release escrow, or issue a refund.
     */
    @Transactional
    public void recordManualReviewEvidence(
            long operationId,
            String evidenceReference,
            String finding) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof CurrentUser)
                || authentication.getAuthorities().stream()
                        .noneMatch(authority ->
                                "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Administrator access is required.");
        }

        if (evidenceReference == null || evidenceReference.isBlank()
                || finding == null || finding.isBlank()) {
            throw new IllegalArgumentException(
                    "Evidence reference and finding are required.");
        }

        EscrowReleaseOperation snapshot =
                operations.findById(operationId).orElseThrow();

        EscrowPayment escrow =
                escrows.findById(snapshot.getEscrowId()).orElseThrow();

        Campaign campaign =
                campaigns.findByIdForUpdate(escrow.getCampaignId()).orElseThrow();

        EscrowReleaseOperation operation =
                operations.findByEscrowIdForUpdate(escrow.getId()).orElseThrow();

        if (!Objects.equals(operation.getId(), operationId)
                || operation.getNeedsReviewAt() == null
                || operation.getStatus() == PayoutStatus.CONFIRMED
                || escrow.getStatus() != EscrowStatus.HELD) {
            throw new IllegalStateException(
                    "This payout is not eligible for manual review.");
        }

        audit.record(
                "PAYOUT_MANUAL_REVIEW_EVIDENCE",
                "escrow_release_operation",
                operation.getId(),
                Map.of(
                        "campaignId", campaign.getId(),
                        "operationId", operation.getId(),
                        "escrowId", escrow.getId(),
                        "evidenceReference", evidenceReference,
                        "finding", finding
                )
        );
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
