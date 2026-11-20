package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flavors.dto.ProofReviewRequest;
import com.flavors.dto.ProofReviewAgreementResponse;
import com.flavors.entity.CampaignDeliverable;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import com.flavors.entity.Campaign;
import com.flavors.entity.ProofReview;
import com.flavors.entity.ProofSubmission;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofReviewFollowUp;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.mapper.ProofReviewMapper;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.ProofReviewRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.EscrowReleaseService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ProofReviewServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final ProofSubmissionRepository submissions = mock(ProofSubmissionRepository.class);
    private final ProofReviewRepository reviews = mock(ProofReviewRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final EscrowReleaseService releases = mock(EscrowReleaseService.class);
    private final List<ProofReview> saved = new ArrayList<>();
    private final ProofReviewServiceImpl service = new ProofReviewServiceImpl(campaigns, submissions, reviews,
            new ProofReviewMapper(), audit, releases, Clock.fixed(NOW, ZoneOffset.UTC));
    private Campaign campaign;
    private ProofSubmission submission;

    @BeforeEach
    void setUp() {
        campaign = BeanUtils.instantiateClass(Campaign.class);
        field(campaign, "id", 42L);
        field(campaign, "brandId", 9L);
        field(campaign, "creatorId", 7L);
        field(campaign, "status", CampaignStatus.PROOF_SUBMITTED);
        field(campaign, "proofReviewDeadline", NOW.plusSeconds(60));
        submission = submitted(5, 42, 1);
        when(campaigns.findByIdAndBrandIdForUpdate(42L, 9L)).thenReturn(Optional.of(campaign));
        when(campaigns.findById(42L)).thenReturn(Optional.of(campaign));
        when(submissions.findById(5L)).thenReturn(Optional.of(submission));
        when(submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(anyLong(), any()))
                .thenAnswer(ignored -> Optional.of(submission));
        when(reviews.findBySubmissionId(anyLong())).thenAnswer(call -> saved.stream()
                .filter(review -> review.getSubmissionId().equals(call.getArgument(0))).findFirst());
        when(reviews.findByCampaignId(42L)).thenAnswer(ignored -> List.copyOf(saved));
        when(reviews.saveAndFlush(any(ProofReview.class))).thenAnswer(call -> {
            ProofReview review = call.getArgument(0);
            field(review, "id", 100L + saved.size());
            saved.add(review);
            return review;
        });
    }

    @Test
    void approvalRecordsTheDecisionAndRequiredPaymentFollowUpWithoutCompletingTheCampaign() {
        var result = service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null));

        assertThat(result.decision()).isEqualTo(ProofReviewDecision.APPROVED);
        assertThat(saved.getFirst().getDecidedBy()).isEqualTo(9);
        assertThat(result.decidedAt()).isEqualTo(NOW);
        assertThat(result.requiredFollowUp()).isEqualTo(ProofReviewFollowUp.RELEASE_PAYMENT);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PROOF_SUBMITTED);
        assertThat(campaign.getProofReviewDeadline()).isNull();
        verify(audit).record("PROOF_APPROVED", "proof_review", 100L,
                java.util.Map.of("campaignId", 42L, "submissionId", 5L, "round", 1));
        verify(releases).authorizeApproved(42, 5, 100);
    }

    @Test
    void correctionsOpenTheNextRoundAndClearTheOldTimer() {
        var result = service.review(9, 42, 5, request(ProofReviewDecision.CORRECTIONS_REQUESTED, " Fix caption "));

        assertThat(result.reason()).isEqualTo("Fix caption");
        assertThat(result.requiredFollowUp()).isEqualTo(ProofReviewFollowUp.RESUBMIT_PROOF);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CORRECTIONS_REQUESTED);
        assertThat(campaign.getCorrectionRoundsUsed()).isEqualTo(1);
        assertThat(campaign.currentProofRound()).isEqualTo(2);
        assertThat(campaign.getProofReviewDeadline()).isNull();
        assertThat(service.history(9, Role.BRAND, 42).availableDecisions()).isEmpty();
    }

    @Test
    void theSecondCorrectionRequestIsAllowed() {
        round(2);

        service.review(9, 42, 5, request(ProofReviewDecision.CORRECTIONS_REQUESTED, null));

        assertThat(campaign.getCorrectionRoundsUsed()).isEqualTo(2);
        assertThat(campaign.currentProofRound()).isEqualTo(3);
    }

    @Test
    void aThirdCorrectionRequestIsRefusedAndNotSaved() {
        round(3);

        assertThatThrownBy(() -> service.review(9, 42, 5,
                request(ProofReviewDecision.CORRECTIONS_REQUESTED, "Again")))
                .isInstanceOf(DeliveryProofConflictException.class).hasMessageContaining("Two correction rounds");

        assertThat(campaign.getCorrectionRoundsUsed()).isEqualTo(2);
        assertThat(service.history(9, Role.BRAND, 42).availableDecisions())
                .containsExactly(ProofReviewDecision.APPROVED, ProofReviewDecision.REJECTED);
        verify(reviews, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = ProofReviewDecision.class, names = {"APPROVED", "REJECTED"})
    void approveOrRejectIsStillAllowedOnTheThirdSubmission(ProofReviewDecision decision) {
        round(3);

        assertThat(service.review(9, 42, 5, request(decision, "Final decision")).decision()).isEqualTo(decision);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectionNeedsAMeaningfulReason(String reason) {
        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.REJECTED, reason)))
                .isInstanceOf(ValidationFailedException.class).hasMessageContaining("reason is required");

        assertThat(campaign.getProofReviewDeadline()).isNotNull();
        verify(reviews, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectionDoesNotPretendADisputeHasAlreadyOpened() {
        var result = service.review(9, 42, 5, request(ProofReviewDecision.REJECTED, "  Post was removed  "));

        assertThat(result.reason()).isEqualTo("Post was removed");
        assertThat(result.requiredFollowUp()).isEqualTo(ProofReviewFollowUp.FREEZE_PAYMENT_AND_OPEN_DISPUTE);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PROOF_SUBMITTED);
        assertThat(campaign.getProofReviewDeadline()).isNull();
        verify(releases).freezeForRejection(42, 5, 100);
    }

    @ParameterizedTest
    @EnumSource(ProofReviewDecision.class)
    void identicalRetriesReturnTheOriginalDecisionAndNeverRepeatItsEffects(ProofReviewDecision decision) {
        var original = service.review(9, 42, 5, request(decision, " Reason "));
        var retry = service.review(9, 42, 5, request(decision, "Reason"));

        assertThat(retry).isEqualTo(original);
        assertThat(saved).hasSize(1);
        assertThat(campaign.getCorrectionRoundsUsed())
                .isEqualTo(decision == ProofReviewDecision.CORRECTIONS_REQUESTED ? 1 : 0);
        verify(reviews, times(1)).saveAndFlush(any());
        verify(audit, times(1)).record(any(), any(), any(), any());
        if (decision == ProofReviewDecision.REJECTED) {
            verify(releases, times(2)).freezeForRejection(42, 5, 100);
        } else {
            verify(releases, never()).freezeForRejection(anyLong(), anyLong(), anyLong());
        }
    }

    @Test
    void aRecordedDecisionCannotBeReplaced() {
        service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null));

        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.REJECTED, "Changed mind")))
                .isInstanceOf(DeliveryProofConflictException.class).hasMessageContaining("different decision");
        assertThat(saved).hasSize(1);
    }

    @Test
    void anotherBrandCannotReadOrDecideOnThisCampaign() {
        assertThatThrownBy(() -> service.review(10, 42, 5, request(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> service.history(10, Role.BRAND, 42))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> service.history(8, Role.CREATOR, 42))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        verify(campaigns).findByIdAndBrandIdForUpdate(42L, 10L);
        verify(campaigns, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(reviews, audit, submissions);
    }

    @Test
    void decisionAndCampaignUpdateUsePostgresMicrosecondPrecision() {
        Clock preciseClock = Clock.fixed(Instant.parse("2026-11-07T08:00:00.123456789Z"), ZoneOffset.UTC);
        var preciseService = new ProofReviewServiceImpl(campaigns, submissions, reviews,
                new ProofReviewMapper(), audit, releases, preciseClock);

        var result = preciseService.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null));

        Instant expected = Instant.parse("2026-11-07T08:00:00.123456Z");
        assertThat(result.decidedAt()).isEqualTo(expected);
        assertThat(saved.getFirst().getDecidedAt()).isEqualTo(expected);
        assertThat(ReflectionTestUtils.getField(campaign, "updatedAt")).isEqualTo(expected);
    }

    @Test
    void creatorsAndAdminsCanReadTheDecisionAndReasonButCannotReviewThroughHistory() {
        service.review(9, 42, 5, request(ProofReviewDecision.CORRECTIONS_REQUESTED, "Add the agreed caption"));

        var creator = service.history(7, Role.CREATOR, 42);
        var admin = service.history(11, Role.ADMIN, 42);
        assertThat(creator.reviews()).singleElement().satisfies(review ->
                assertThat(review.reason()).isEqualTo("Add the agreed caption"));
        assertThat(admin.reviews()).isEqualTo(creator.reviews());
        assertThat(creator.availableDecisions()).isEmpty();
        assertThat(admin.availableDecisions()).isEmpty();
    }

    @Test
    void manualReviewIncludesTheAgreedPlatformDeliverablesAndDates() {
        field(campaign, "startDate", java.time.LocalDate.of(2026, 11, 1));
        field(campaign, "lastPostingDate", java.time.LocalDate.of(2026, 11, 8));
        field(campaign, "contentRequirements", "Mention the branch");
        field(campaign, "screenshotsRequired", true);
        field(campaign, "deliverables", new ArrayList<>(List.of(
                new CampaignDeliverable(DeliverablePlatform.INSTAGRAM, DeliverableType.REEL, 2))));

        assertThat(service.history(9, Role.BRAND, 42).agreement()).isEqualTo(new ProofReviewAgreementResponse(
                java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 8), "Mention the branch", true,
                List.of(new ProofReviewAgreementResponse.Deliverable(DeliverablePlatform.INSTAGRAM,
                        DeliverableType.REEL, 2))));
    }

    @Test
    void aCampaignStillInDraftShowsTheCreatorNothingNotEvenTheAgreement() {
        field(campaign, "status", CampaignStatus.DRAFT);
        field(campaign, "contentRequirements", "Not for the creator yet");

        assertThatThrownBy(() -> service.history(7, Role.CREATOR, 42))
                .isInstanceOf(DeliveryProofNotFoundException.class);
    }

    @Test
    void aDraftCannotBeReviewed() {
        submission = new ProofSubmission(42, 1);
        field(submission, "id", 5L);
        when(submissions.findById(5L)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        verifyNoInteractions(reviews, audit);
    }

    @Test
    void aSubmissionIdFromAnotherCampaignCannotBeReviewed() {
        when(submissions.findById(5L)).thenReturn(Optional.of(submitted(5, 99, 1)));

        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        verifyNoInteractions(reviews, audit);
    }

    @Test
    void anEarlierUnreviewedVersionCannotBeDecidedAfterResubmission() {
        round(2);
        when(submissions.findById(4L)).thenReturn(Optional.of(submitted(4, 42, 1)));

        assertThatThrownBy(() -> service.review(9, 42, 4, request(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofConflictException.class).hasMessageContaining("latest proof");
        verify(reviews, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROOF_SUBMITTED")
    void decisionsRequireACampaignAwaitingProofReview(CampaignStatus status) {
        field(campaign, "status", status);

        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofConflictException.class);
        verify(reviews, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -86400})
    void decisionsAtOrAfterTheDeadlineAreRefused(long secondsUntilDeadline) {
        field(campaign, "proofReviewDeadline", NOW.plusSeconds(secondsUntilDeadline));

        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.REJECTED, "Late")))
                .isInstanceOf(DeliveryProofConflictException.class).hasMessage("The five-day review period has ended.");
        assertThat(service.history(9, Role.BRAND, 42).availableDecisions()).isEmpty();
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void anIdenticalRetryStillWorksAfterItsOriginalDeadline() {
        var result = service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null));
        field(campaign, "proofReviewDeadline", NOW.minusSeconds(1));

        assertThat(service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null))).isEqualTo(result);
    }

    @Test
    void aMissingDeadlineDoesNotAllowANewDecision() {
        field(campaign, "proofReviewDeadline", null);

        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofConflictException.class);
    }

    @Test
    void blankDecisionsAndOverlongReasonsAreValidatedAtTheServiceBoundary() {
        assertThatThrownBy(() -> service.review(9, 42, 5, null))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.review(9, 42, 5, request(null, null)))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.review(9, 42, 5, request(ProofReviewDecision.REJECTED, "x".repeat(2001))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void overdueCandidatesAreBoundedAndReadWithoutFinancialSideEffects() {
        when(campaigns.findOverdueProofCampaignIds(NOW, PageRequest.of(0, 100))).thenReturn(List.of(42L));

        assertThat(service.findOverdueCampaignIds()).containsExactly(42L);
        verifyNoInteractions(reviews, submissions, audit);
    }

    @Test
    void aNewSubmissionRestartsTheDeadlineFromItsOwnSubmissionTime() {
        service.review(9, 42, 5, request(ProofReviewDecision.CORRECTIONS_REQUESTED, null));
        Instant resubmitted = NOW.plus(Duration.ofDays(2));

        campaign.markProofSubmitted(resubmitted, resubmitted.plus(Duration.ofDays(5)));

        assertThat(campaign.getProofReviewDeadline()).isEqualTo(resubmitted.plus(Duration.ofDays(5)));
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PROOF_SUBMITTED);
    }

    private void round(int round) {
        field(campaign, "correctionRoundsUsed", (short) (round - 1));
        submission = submitted(5, 42, round);
        when(submissions.findById(5L)).thenReturn(Optional.of(submission));
    }

    private static ProofSubmission submitted(long id, long campaignId, int round) {
        ProofSubmission submission = new ProofSubmission(campaignId, round);
        field(submission, "id", id);
        submission.submit(NOW.minusSeconds(30));
        return submission;
    }

    private static ProofReviewRequest request(ProofReviewDecision decision, String reason) {
        return new ProofReviewRequest(decision, reason);
    }

    private static void field(Object object, String name, Object value) {
        ReflectionTestUtils.setField(object, name, value);
    }
}
