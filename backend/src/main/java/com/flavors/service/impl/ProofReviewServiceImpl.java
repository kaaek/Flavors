package com.flavors.service.impl;

import com.flavors.dto.CampaignProofReviewsResponse;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.dto.ProofReviewResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.ProofReview;
import com.flavors.entity.ProofSubmission;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.mapper.ProofReviewMapper;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.ProofReviewRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.ProofReviewService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProofReviewServiceImpl implements ProofReviewService {

    private final CampaignRepository campaigns;
    private final ProofSubmissionRepository submissions;
    private final ProofReviewRepository reviews;
    private final ProofReviewMapper mapper;
    private final AuditLogger audit;
    private final Clock clock;

    @Autowired
    public ProofReviewServiceImpl(CampaignRepository campaigns, ProofSubmissionRepository submissions,
                                  ProofReviewRepository reviews, ProofReviewMapper mapper, AuditLogger audit) {
        this(campaigns, submissions, reviews, mapper, audit, Clock.systemUTC());
    }

    ProofReviewServiceImpl(CampaignRepository campaigns, ProofSubmissionRepository submissions,
                           ProofReviewRepository reviews, ProofReviewMapper mapper, AuditLogger audit,
                           Clock clock) {
        this.campaigns = campaigns;
        this.submissions = submissions;
        this.reviews = reviews;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProofReviewResponse review(long brandId, long campaignId, long submissionId, ProofReviewRequest request) {
        // Filter ownership in SQL so another brand cannot lock this row. This is the same row lock as EEC-46.
        Campaign campaign = campaigns.findByIdAndBrandIdForUpdate(campaignId, brandId)
                .orElseThrow(DeliveryProofNotFoundException::new);
        ProofSubmission submission = submissions.findById(submissionId)
                .filter(found -> found.getCampaignId().equals(campaignId)
                        && found.getStatus() == ProofSubmissionStatus.SUBMITTED)
                .orElseThrow(DeliveryProofNotFoundException::new);
        ProofReviewDecision decision = validateDecision(request);
        String reason = normalizeReason(request.reason(), decision);

        // The submission id is the natural retry key. A lost HTTP response must not consume a second round.
        var existing = reviews.findBySubmissionId(submissionId);
        if (existing.isPresent()) {
            ProofReview saved = existing.get();
            if (saved.getDecision() != decision || !Objects.equals(saved.getReason(), reason)) {
                throw new DeliveryProofConflictException("This proof version already has a different decision.");
            }
            return mapper.toResponse(saved);
        }

        ProofSubmission latest = latestSubmission(campaignId);
        // PostgreSQL stores microseconds; the first response and a database-backed retry must be identical.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (campaign.getStatus() != CampaignStatus.PROOF_SUBMITTED || latest == null
                || latest.getId() != submissionId || submission.getRound() != campaign.currentProofRound()) {
            throw new DeliveryProofConflictException("Only the latest proof awaiting review can be reviewed.");
        }
        if (!beforeDeadline(campaign, now)) {
            throw new DeliveryProofConflictException(
                    "The five-day review period has ended.");
        }
        if (decision == ProofReviewDecision.CORRECTIONS_REQUESTED) {
            if (campaign.getCorrectionRoundsUsed() >= 2) {
                throw new DeliveryProofConflictException("Two correction rounds have been used. Approve or reject.");
            }
            campaign.requestProofCorrections(now);
        } else {
            // Record the response without claiming that payment or dispute side effects already happened.
            // The review is durable input for UC-F7.2/UC-F8.1; no external call belongs in this transaction.
            campaign.recordProofReview(now);
        }
        ProofReview saved = reviews.saveAndFlush(new ProofReview(submissionId, decision, reason, brandId, now));
        audit.record("PROOF_" + decision.name(), "proof_review", saved.getId(), Map.of(
                "campaignId", campaignId, "submissionId", submissionId, "round", submission.getRound()));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignProofReviewsResponse history(long userId, Role role, long campaignId) {
        Campaign campaign = campaigns.findById(campaignId).orElseThrow(DeliveryProofNotFoundException::new);
        boolean owns = role == Role.ADMIN
                || (role == Role.BRAND && campaign.getBrandId().equals(userId))
                || (role == Role.CREATOR && DeliveryProofServiceImpl.visibleToCreator(campaign, userId));
        if (!owns) {
            throw new DeliveryProofNotFoundException();
        }
        ProofSubmission latest = latestSubmission(campaignId);
        List<ProofReview> history = reviews.findByCampaignId(campaignId);
        boolean latestReviewed = latest != null && history.stream()
                .anyMatch(review -> review.getSubmissionId().equals(latest.getId()));
        boolean canReview = role == Role.BRAND && campaign.getStatus() == CampaignStatus.PROOF_SUBMITTED
                && latest != null && latest.getRound() == campaign.currentProofRound()
                && !latestReviewed && beforeDeadline(campaign, clock.instant());
        List<ProofReviewDecision> available = List.of();
        if (canReview) {
            available = campaign.getCorrectionRoundsUsed() >= 2
                    ? List.of(ProofReviewDecision.APPROVED, ProofReviewDecision.REJECTED)
                    : List.of(ProofReviewDecision.APPROVED, ProofReviewDecision.CORRECTIONS_REQUESTED,
                            ProofReviewDecision.REJECTED);
        }
        return new CampaignProofReviewsResponse(campaignId, campaign.getStatus(), campaign.getCorrectionRoundsUsed(),
                campaign.getProofReviewDeadline(), latest == null ? null : latest.getId(),
                mapper.toAgreement(campaign), available, history.stream().map(mapper::toResponse).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverdueCampaignIds() {
        // UC-F7.2 must re-lock and recheck each candidate before releasing funds; this is only a snapshot.
        return campaigns.findOverdueProofCampaignIds(clock.instant(), PageRequest.of(0, 100));
    }

    private ProofSubmission latestSubmission(long campaignId) {
        return submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(campaignId, ProofSubmissionStatus.SUBMITTED)
                .orElse(null);
    }

    private static boolean beforeDeadline(Campaign campaign, Instant now) {
        return campaign.getProofReviewDeadline() != null && now.isBefore(campaign.getProofReviewDeadline());
    }

    private static ProofReviewDecision validateDecision(ProofReviewRequest request) {
        if (request == null || request.decision() == null) {
            throw new ValidationFailedException("decision", "Choose approve, request corrections or reject.");
        }
        return request.decision();
    }

    private static String normalizeReason(String value, ProofReviewDecision decision) {
        if (value != null && value.length() > 2000) {
            throw new ValidationFailedException("reason", "The reason cannot exceed 2000 characters.");
        }
        String reason = value == null || value.isBlank() ? null : value.trim();
        if (decision == ProofReviewDecision.REJECTED && reason == null) {
            throw new ValidationFailedException("reason", "A reason is required to reject delivered work.");
        }
        return reason;
    }
}
