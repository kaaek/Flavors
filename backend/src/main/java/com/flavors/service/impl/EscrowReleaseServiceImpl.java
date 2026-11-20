package com.flavors.service.impl;

import com.flavors.entity.Campaign;
import com.flavors.entity.EscrowPayment;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.entity.ProofReview;
import com.flavors.entity.ProofSubmission;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.ReleaseAuthorizationSource;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.EscrowPaymentRepository;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.repository.ProofReviewRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.EscrowReleaseService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class EscrowReleaseServiceImpl implements EscrowReleaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EscrowReleaseServiceImpl.class);
    private static final int RECOVERY_BATCH_SIZE = 100;

    private final CampaignRepository campaigns;
    private final EscrowPaymentRepository escrows;
    private final ProofSubmissionRepository submissions;
    private final ProofReviewRepository reviews;
    private final EscrowReleaseOperationRepository operations;
    private final AuditLogger audit;
    private final TransactionOperations tx;
    private final Clock clock;

    @Autowired
    public EscrowReleaseServiceImpl(CampaignRepository campaigns, EscrowPaymentRepository escrows,
                                    ProofSubmissionRepository submissions, ProofReviewRepository reviews,
                                    EscrowReleaseOperationRepository operations, AuditLogger audit,
                                    TransactionOperations tx) {
        this(campaigns, escrows, submissions, reviews, operations, audit, tx, Clock.systemUTC());
    }

    EscrowReleaseServiceImpl(CampaignRepository campaigns, EscrowPaymentRepository escrows,
                            ProofSubmissionRepository submissions, ProofReviewRepository reviews,
                            EscrowReleaseOperationRepository operations, AuditLogger audit,
                            TransactionOperations tx, Clock clock) {
        this.campaigns = campaigns;
        this.escrows = escrows;
        this.submissions = submissions;
        this.reviews = reviews;
        this.operations = operations;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean authorizeApproved(long campaignId, long submissionId, long reviewId) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId)
                .orElseThrow(DeliveryProofNotFoundException::new);
        ProofSubmission submission = submissions.findById(submissionId)
                .filter(s -> Objects.equals(s.getCampaignId(), campaignId)
                        && s.getStatus() == ProofSubmissionStatus.SUBMITTED)
                .orElseThrow(DeliveryProofNotFoundException::new);
        ProofReview review = reviews.findById(reviewId)
                .filter(r -> Objects.equals(r.getSubmissionId(), submissionId)
                        && r.getDecision() == ProofReviewDecision.APPROVED
                        && Objects.equals(r.getDecidedBy(), campaign.getBrandId()))
                .orElseThrow(() -> conflict("The current proof must have an approval from the owning brand."));
        ProofSubmission latest = submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(
                campaignId, ProofSubmissionStatus.SUBMITTED).orElseThrow(DeliveryProofNotFoundException::new);
        if (!Objects.equals(latest.getId(), submissionId) || submission.getRound() != campaign.currentProofRound()) {
            throw conflict("Only the latest current proof can authorize release.");
        }
        if (reviews.findByCampaignId(campaignId).stream()
                .anyMatch(r -> r.getDecision() == ProofReviewDecision.REJECTED)) {
            throw conflict("A rejected campaign cannot authorize release.");
        }
        EscrowPayment escrow = escrows.findByCampaignId(campaignId)
                .orElseThrow(() -> conflict("No held escrow exists for this campaign."));

        // Read after the campaign lock. The natural operation key plus V10's unique escrow constraint
        // is the idempotency boundary here. Do not acquire an idempotency-row lock in reverse order.
        var existing = operations.findByEscrowId(escrow.getId());
        if (existing.isPresent()) {
            EscrowReleaseOperation operation = existing.get();
            if (operation.getAuthorizationSource() != ReleaseAuthorizationSource.APPROVAL
                    || !Objects.equals(operation.getSubmissionId(), submissionId)
                    || !Objects.equals(operation.getReviewId(), reviewId)
                    || operation.getOriginalAmountCents() != escrow.getAmountCents()) {
                throw conflict("A different release operation already exists for this escrow.");
            }
            // Replaying a real authorization remains harmless even after later payout processing.
            return false;
        }
        if (campaign.getStatus() != CampaignStatus.PROOF_SUBMITTED || escrow.getStatus() != EscrowStatus.HELD
                || !Objects.equals(campaign.getAgreedPriceCents(), escrow.getAmountCents())) {
            throw conflict("Release authorization requires approved proof and the campaign's held agreed amount.");
        }
        return createAuthorization(campaign, escrow, submission, ReleaseAuthorizationSource.APPROVAL, review.getId(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean authorizeOverdue(long campaignId) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId).orElse(null);
        // Read time after obtaining the lock: the candidate query is only a snapshot.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (campaign == null || campaign.getStatus() != CampaignStatus.PROOF_SUBMITTED
                || campaign.getProofReviewDeadline() == null || now.isBefore(campaign.getProofReviewDeadline())) {
            return false;
        }
        ProofSubmission latest = submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(
                campaignId, ProofSubmissionStatus.SUBMITTED).orElse(null);
        if (latest == null || latest.getRound() != campaign.currentProofRound()
                || reviews.findBySubmissionId(latest.getId()).isPresent()
                || reviews.findByCampaignId(campaignId).stream()
                    .anyMatch(r -> r.getDecision() == ProofReviewDecision.REJECTED)) {
            return false;
        }
        EscrowPayment escrow = escrows.findByCampaignId(campaignId).orElse(null);
        if (escrow == null || escrow.getStatus() != EscrowStatus.HELD
                || !Objects.equals(campaign.getAgreedPriceCents(), escrow.getAmountCents())
                || operations.findByEscrowId(escrow.getId()).isPresent()) {
            return false;
        }
        createAuthorization(campaign, escrow, latest, ReleaseAuthorizationSource.TIMER_C, null, now);
        // Ends only this review timer; no synthetic brand review, escrow transition, or payout.
        campaign.recordProofReview(now);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean freezeForRejection(long campaignId, long submissionId, long reviewId) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId)
                .orElseThrow(DeliveryProofNotFoundException::new);
        ProofSubmission submission = submissions.findById(submissionId)
                .filter(s -> Objects.equals(s.getCampaignId(), campaignId)
                        && s.getStatus() == ProofSubmissionStatus.SUBMITTED)
                .orElseThrow(DeliveryProofNotFoundException::new);
        reviews.findById(reviewId)
                .filter(r -> Objects.equals(r.getSubmissionId(), submissionId)
                        && r.getDecision() == ProofReviewDecision.REJECTED
                        && Objects.equals(r.getDecidedBy(), campaign.getBrandId()))
                .orElseThrow(() -> conflict("The current proof must have a rejection from the owning brand."));
        ProofSubmission latest = submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(
                campaignId, ProofSubmissionStatus.SUBMITTED).orElseThrow(DeliveryProofNotFoundException::new);
        if (!Objects.equals(latest.getId(), submissionId) || submission.getRound() != campaign.currentProofRound()) {
            throw conflict("Only the latest current rejected proof can freeze escrow.");
        }
        // The review service checked ownership and deadline while holding this same campaign lock.
        // Replays may repair a legacy rejection after its original deadline; never invent a new review.
        EscrowPayment escrow = escrows.findByCampaignId(campaignId)
                .orElseThrow(() -> conflict("No held escrow exists for this campaign."));
        if (operations.findByEscrowId(escrow.getId()).isPresent()) {
            throw conflict("An authorized release prevents rejection freezing.");
        }
        if (escrow.getStatus() == EscrowStatus.FROZEN) {
            return false;
        }
        if (campaign.getStatus() != CampaignStatus.PROOF_SUBMITTED || escrow.getStatus() != EscrowStatus.HELD) {
            throw conflict("Rejection freezing requires submitted proof and held escrow.");
        }
        escrow.freeze(clock.instant().truncatedTo(ChronoUnit.MICROS));
        audit.record("ESCROW_FROZEN", "escrow_payment", escrow.getId(), java.util.Map.of(
                "campaignId", campaignId, "submissionId", submissionId, "reviewId", reviewId,
                "previousStatus", EscrowStatus.HELD.name(), "status", EscrowStatus.FROZEN.name()));
        return true;
    }

    /** Shared database-only creation point for approval and Timer C. */
    private boolean createAuthorization(Campaign campaign, EscrowPayment escrow, ProofSubmission submission,
                                         ReleaseAuthorizationSource source, Long reviewId, Instant now) {
        // The Step 2 entity snapshots Money.percent(15) and subtracts the fee once, preserving every cent.
        EscrowReleaseOperation operation = operations.saveAndFlush(new EscrowReleaseOperation(
                escrow.getId(), submission.getId(), reviewId, source,
                escrow.getAmountCents(), now));
        var details = new LinkedHashMap<String, Object>();
        details.put("campaignId", campaign.getId());
        details.put("escrowId", escrow.getId());
        details.put("submissionId", submission.getId());
        if (reviewId != null) {
            details.put("reviewId", reviewId);
        }
        details.put("source", source.name());
        details.put("amountCents", operation.getOriginalAmountCents());
        details.put("feeCents", operation.getFeeCents());
        details.put("creatorNetCents", operation.getCreatorNetCents());
        audit.record("ESCROW_RELEASE_AUTHORIZED", "escrow_release_operation", operation.getId(), details);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int recoverApprovedReviews() {
        int recovered = 0;
        long afterReviewId = 0;
        while (true) {
            var candidates = reviews.findApprovedReleaseCandidates(afterReviewId, PageRequest.of(0, RECOVERY_BATCH_SIZE));
            if (candidates.isEmpty()) {
                return recovered;
            }
            for (var candidate : candidates) {
                afterReviewId = candidate.getReviewId();
                try {
                    // This method suspends any caller transaction. Each candidate commits/rolls back independently.
                    if (Boolean.TRUE.equals(tx.execute(s -> authorizeApproved(candidate.getCampaignId(),
                            candidate.getSubmissionId(), candidate.getReviewId())))) {
                        recovered++;
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not recover release authorization for campaign {} ({}); it remains retryable.",
                            candidate.getCampaignId(), e.getClass().getSimpleName());
                }
            }
        }
    }

    private static DeliveryProofConflictException conflict(String message) {
        return new DeliveryProofConflictException(message);
    }
}
