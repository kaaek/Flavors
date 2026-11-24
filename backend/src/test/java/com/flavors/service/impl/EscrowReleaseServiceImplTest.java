package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flavors.entity.Campaign;
import com.flavors.entity.EscrowPayment;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.entity.ProofReview;
import com.flavors.entity.ProofSubmission;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PayoutStatus;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class EscrowReleaseServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final EscrowPaymentRepository escrows = mock(EscrowPaymentRepository.class);
    private final ProofSubmissionRepository submissions = mock(ProofSubmissionRepository.class);
    private final ProofReviewRepository reviews = mock(ProofReviewRepository.class);
    private final EscrowReleaseOperationRepository operations = mock(EscrowReleaseOperationRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final TransactionOperations tx = mock(TransactionOperations.class);
    private final EscrowReleaseServiceImpl service = new EscrowReleaseServiceImpl(campaigns, escrows, submissions,
            reviews, operations, audit, tx, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Campaign campaign = mock(Campaign.class);
    private final EscrowPayment escrow = mock(EscrowPayment.class);
    private final ProofSubmission submission = mock(ProofSubmission.class);
    private final ProofReview review = mock(ProofReview.class);

    @BeforeEach
    void setUp() {
        when(campaigns.findByIdForUpdate(42L)).thenReturn(Optional.of(campaign));
        when(campaign.getId()).thenReturn(42L);
        when(campaign.getBrandId()).thenReturn(9L);
        when(campaign.getStatus()).thenReturn(CampaignStatus.PROOF_SUBMITTED);
        when(campaign.getAgreedPriceCents()).thenReturn(20000L);
        when(campaign.currentProofRound()).thenReturn(1);
        when(submissions.findById(5L)).thenReturn(Optional.of(submission));
        when(submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(42L, ProofSubmissionStatus.SUBMITTED))
                .thenReturn(Optional.of(submission));
        when(submission.getId()).thenReturn(5L);
        when(submission.getCampaignId()).thenReturn(42L);
        when(submission.getStatus()).thenReturn(ProofSubmissionStatus.SUBMITTED);
        when(submission.getRound()).thenReturn(1);
        when(reviews.findById(8L)).thenReturn(Optional.of(review));
        when(review.getId()).thenReturn(8L);
        when(review.getSubmissionId()).thenReturn(5L);
        when(review.getDecision()).thenReturn(ProofReviewDecision.APPROVED);
        when(review.getDecidedBy()).thenReturn(9L);
        when(reviews.findByCampaignId(42L)).thenReturn(List.of(review));
        when(escrows.findByCampaignId(42L)).thenReturn(Optional.of(escrow));
        when(escrow.getId()).thenReturn(10L);
        when(escrow.getStatus()).thenReturn(EscrowStatus.HELD);
        when(escrow.getAmountCents()).thenReturn(20000L);
        when(operations.saveAndFlush(any())).thenAnswer(call -> {
            EscrowReleaseOperation saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    @Test
    void authorizesAndAuditsTheFeeSnapshotWithoutChangingEscrow() {
        assertThat(service.authorizeApproved(42, 5, 8)).isTrue();
        var captured = ArgumentCaptor.forClass(EscrowReleaseOperation.class);
        verify(operations).saveAndFlush(captured.capture());
        var operation = captured.getValue();
        assertThat(operation.getFeeCents()).isEqualTo(3000);
        assertThat(operation.getCreatorNetCents()).isEqualTo(17000);
        assertThat(operation.getStatus()).isEqualTo(PayoutStatus.AUTHORIZED);
        assertThat(operation.getAuthorizedAt()).isEqualTo(NOW);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("ESCROW_RELEASE_AUTHORIZED"),
                org.mockito.ArgumentMatchers.eq("escrow_release_operation"), org.mockito.ArgumentMatchers.eq(99L), any());
        verify(escrow, never()).hold(any());
    }

    @Test
    void anIdenticalAuthorizationDoesNotSaveOrAuditAgain() {
        when(operations.findByEscrowId(10L)).thenReturn(Optional.of(new EscrowReleaseOperation(
                10, 5, 8L, ReleaseAuthorizationSource.APPROVAL, 20000, NOW.minusSeconds(60))));
        assertThat(service.authorizeApproved(42, 5, 8)).isFalse();
        verify(operations, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = EscrowStatus.class, names = {"HELD"}, mode = EnumSource.Mode.EXCLUDE)
    void onlyHeldEscrowCanCreateAnAuthorization(EscrowStatus status) {
        when(escrow.getStatus()).thenReturn(status);
        assertRefused();
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class, names = {"PROOF_SUBMITTED"}, mode = EnumSource.Mode.EXCLUDE)
    void onlyACampaignAwaitingProofReviewCanCreateAnAuthorization(CampaignStatus status) {
        when(campaign.getStatus()).thenReturn(status);
        assertRefused();
    }

    @ParameterizedTest
    @EnumSource(value = ProofReviewDecision.class, names = {"APPROVED"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectionOrCorrectionsAreNotApproval(ProofReviewDecision decision) {
        when(review.getDecision()).thenReturn(decision);
        assertRefused();
    }

    @Test
    void anotherBrandsReviewOrAnEarlierRoundCannotAuthorizeRelease() {
        when(review.getDecidedBy()).thenReturn(100L);
        assertRefused();
        when(review.getDecidedBy()).thenReturn(9L);
        when(campaign.currentProofRound()).thenReturn(2);
        assertRefused();
    }

    @Test
    void foreignCampaignProofAndMissingCampaignAreNotFound() {
        when(submission.getCampaignId()).thenReturn(100L);
        assertThatThrownBy(() -> service.authorizeApproved(42, 5, 8))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> service.authorizeApproved(100, 5, 8))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void conflictingReleaseRejectionOrAgreedAmountPreventsAuthorization() {
        when(campaign.getAgreedPriceCents()).thenReturn(21000L);
        assertRefused();
        when(campaign.getAgreedPriceCents()).thenReturn(20000L);
        when(operations.findByEscrowId(10L)).thenReturn(Optional.of(new EscrowReleaseOperation(
                10, 5, null, ReleaseAuthorizationSource.TIMER_C, 20000, NOW)));
        assertRefused();
        when(operations.findByEscrowId(10L)).thenReturn(Optional.empty());
        ProofReview rejected = mock(ProofReview.class);
        when(rejected.getDecision()).thenReturn(ProofReviewDecision.REJECTED);
        when(reviews.findByCampaignId(42L)).thenReturn(List.of(rejected, review));
        assertRefused();
    }

    @Test
    void recoveryUsesKeysetBatchesAndDatabaseTransactions() {
        ProofReviewRepository.ApprovedReleaseCandidate candidate = mock(ProofReviewRepository.ApprovedReleaseCandidate.class);
        when(candidate.getCampaignId()).thenReturn(42L);
        when(candidate.getSubmissionId()).thenReturn(5L);
        when(candidate.getReviewId()).thenReturn(8L);
        when(reviews.findApprovedReleaseCandidates(0, PageRequest.of(0, 100))).thenReturn(List.of(candidate));
        when(tx.execute(any())).thenAnswer(call -> {
            TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(null);
        });
        assertThat(service.recoverApprovedReviews()).isEqualTo(1);
        verify(reviews).findApprovedReleaseCandidates(8, PageRequest.of(0, 100));
        verify(tx).execute(any());
    }

    @Test
    void rejectionFreezesAndAuditsInTheCallerTransaction() {
        when(review.getDecision()).thenReturn(ProofReviewDecision.REJECTED);
        assertThat(service.freezeForRejection(42, 5, 8)).isTrue();
        verify(escrow).freeze(NOW);
        verify(audit).record("ESCROW_FROZEN", "escrow_payment", 10L, java.util.Map.of(
                "campaignId", 42L, "submissionId", 5L, "reviewId", 8L,
                "previousStatus", "HELD", "status", "FROZEN"));
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void identicalFrozenRejectionDoesNotChangeOrAuditAgain() {
        when(review.getDecision()).thenReturn(ProofReviewDecision.REJECTED);
        when(escrow.getStatus()).thenReturn(EscrowStatus.FROZEN);
        assertThat(service.freezeForRejection(42, 5, 8)).isFalse();
        verify(escrow, never()).freeze(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = EscrowStatus.class, names = {"HELD", "FROZEN"}, mode = EnumSource.Mode.EXCLUDE)
    void otherEscrowStatesCannotBeFrozen(EscrowStatus status) {
        when(review.getDecision()).thenReturn(ProofReviewDecision.REJECTED);
        when(escrow.getStatus()).thenReturn(status);
        assertFreezeRefused();
    }

    @ParameterizedTest
    @EnumSource(value = ProofReviewDecision.class, names = "REJECTED", mode = EnumSource.Mode.EXCLUDE)
    void onlyPersistedRejectionCanFreeze(ProofReviewDecision decision) {
        when(review.getDecision()).thenReturn(decision);
        assertFreezeRefused();
    }

    @Test
    void foreignBrandStaleProofAndAuthorizedReleaseCannotFreeze() {
        when(review.getDecision()).thenReturn(ProofReviewDecision.REJECTED);
        when(review.getDecidedBy()).thenReturn(100L);
        assertFreezeRefused();
        when(review.getDecidedBy()).thenReturn(9L);
        when(campaign.currentProofRound()).thenReturn(2);
        assertFreezeRefused();
        when(campaign.currentProofRound()).thenReturn(1);
        when(operations.findByEscrowId(10L)).thenReturn(Optional.of(new EscrowReleaseOperation(
                10, 5, null, ReleaseAuthorizationSource.TIMER_C, 20000, NOW)));
        assertFreezeRefused();
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class, names = "PROOF_SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void otherCampaignStatesCannotStartAFreeze(CampaignStatus status) {
        when(review.getDecision()).thenReturn(ProofReviewDecision.REJECTED);
        when(campaign.getStatus()).thenReturn(status);
        assertFreezeRefused();
    }

    private void assertFreezeRefused() {
        assertThatThrownBy(() -> service.freezeForRejection(42, 5, 8))
                .isInstanceOf(DeliveryProofConflictException.class);
        verify(escrow, never()).freeze(any());
        verifyNoInteractions(audit);
    }

    private void assertRefused() {
        assertThatThrownBy(() -> service.authorizeApproved(42, 5, 8))
                .isInstanceOf(DeliveryProofConflictException.class);
        verify(operations, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }
}
