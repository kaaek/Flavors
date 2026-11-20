package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flavors.entity.*;
import com.flavors.enums.*;
import com.flavors.repository.*;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class EscrowReleaseTimerCTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final EscrowPaymentRepository escrows = mock(EscrowPaymentRepository.class);
    private final ProofSubmissionRepository submissions = mock(ProofSubmissionRepository.class);
    private final ProofReviewRepository reviews = mock(ProofReviewRepository.class);
    private final EscrowReleaseOperationRepository operations = mock(EscrowReleaseOperationRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final Campaign campaign = mock(Campaign.class);
    private final EscrowPayment escrow = mock(EscrowPayment.class);
    private final ProofSubmission submission = mock(ProofSubmission.class);
    private final EscrowReleaseServiceImpl service = new EscrowReleaseServiceImpl(campaigns, escrows, submissions,
            reviews, operations, audit, mock(TransactionOperations.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(campaigns.findByIdForUpdate(42L)).thenReturn(Optional.of(campaign));
        when(campaign.getId()).thenReturn(42L);
        when(campaign.getStatus()).thenReturn(CampaignStatus.PROOF_SUBMITTED);
        when(campaign.getProofReviewDeadline()).thenReturn(NOW);
        when(campaign.currentProofRound()).thenReturn(1);
        when(campaign.getAgreedPriceCents()).thenReturn(20003L);
        when(submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(42L, ProofSubmissionStatus.SUBMITTED))
                .thenReturn(Optional.of(submission));
        when(submission.getId()).thenReturn(5L);
        when(submission.getRound()).thenReturn(1);
        when(escrows.findByCampaignId(42L)).thenReturn(Optional.of(escrow));
        when(escrow.getId()).thenReturn(10L);
        when(escrow.getStatus()).thenReturn(EscrowStatus.HELD);
        when(escrow.getAmountCents()).thenReturn(20003L);
        when(operations.saveAndFlush(any())).thenAnswer(call -> {
            EscrowReleaseOperation saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    @Test
    void exactDeadlineUsesSharedMoneySnapshotAndAuditsTimerCWithoutABrandReview() {
        assertThat(service.authorizeOverdue(42)).isTrue();
        var captured = ArgumentCaptor.forClass(EscrowReleaseOperation.class);
        verify(operations).saveAndFlush(captured.capture());
        var operation = captured.getValue();
        assertThat(operation.getAuthorizationSource()).isEqualTo(ReleaseAuthorizationSource.TIMER_C);
        assertThat(operation.getReviewId()).isNull();
        assertThat(operation.getFeeCents()).isEqualTo(3000);
        assertThat(operation.getCreatorNetCents()).isEqualTo(17003);
        assertThat(operation.getAuthorizedAt()).isEqualTo(NOW);
        verify(audit).record(eq("ESCROW_RELEASE_AUTHORIZED"), eq("escrow_release_operation"), eq(99L), any());
        verify(campaign).recordProofReview(NOW);
        verify(reviews, never()).save(any());
        verify(escrow, never()).hold(any());
    }

    @Test
    void missingFutureOrClearedDeadlineCannotAuthorize() {
        assertThat(service.authorizeOverdue(999)).isFalse();
        when(campaign.getProofReviewDeadline()).thenReturn(NOW.plusNanos(1000));
        refused();
        when(campaign.getProofReviewDeadline()).thenReturn(null);
        refused();
    }

    @ParameterizedTest
    @EnumSource(value = CampaignStatus.class, names = "PROOF_SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void otherCampaignStatesCannotAuthorize(CampaignStatus status) {
        when(campaign.getStatus()).thenReturn(status);
        refused();
    }

    @ParameterizedTest
    @EnumSource(value = EscrowStatus.class, names = "HELD", mode = EnumSource.Mode.EXCLUDE)
    void onlyHeldEscrowCanAuthorize(EscrowStatus status) {
        when(escrow.getStatus()).thenReturn(status);
        refused();
    }

    @ParameterizedTest
    @EnumSource(ProofReviewDecision.class)
    void anyCurrentBrandDecisionPreventsAutomaticAuthorization(ProofReviewDecision decision) {
        when(reviews.findBySubmissionId(5L)).thenReturn(Optional.of(new ProofReview(5L, decision, null, 9L, NOW)));
        refused();
    }

    @Test
    void missingOrOldSubmissionAndHistoricalRejectionCannotAuthorize() {
        when(submission.getRound()).thenReturn(2);
        refused();
        when(submission.getRound()).thenReturn(1);
        when(reviews.findByCampaignId(42L)).thenReturn(List.of(new ProofReview(4L,
                ProofReviewDecision.REJECTED, "Missing", 9L, NOW)));
        refused();
        when(submissions.findFirstByCampaignIdAndStatusOrderByRoundDesc(42L, ProofSubmissionStatus.SUBMITTED))
                .thenReturn(Optional.empty());
        refused();
    }

    @Test
    void priorCorrectionDoesNotBlockAFreshUnansweredExpiredRound() {
        when(campaign.currentProofRound()).thenReturn(2);
        when(submission.getRound()).thenReturn(2);
        when(reviews.findByCampaignId(42L)).thenReturn(List.of(new ProofReview(4L,
                ProofReviewDecision.CORRECTIONS_REQUESTED, null, 9L, NOW.minusSeconds(500000))));
        assertThat(service.authorizeOverdue(42)).isTrue();
    }

    @Test
    void missingEscrowWrongAmountAndExistingOperationCannotAuthorize() {
        when(campaign.getAgreedPriceCents()).thenReturn(1L);
        refused();
        when(campaign.getAgreedPriceCents()).thenReturn(20003L);
        when(operations.findByEscrowId(10L)).thenReturn(Optional.of(new EscrowReleaseOperation(
                10L, 5L, null, ReleaseAuthorizationSource.TIMER_C, 20003L, NOW.minusSeconds(1))));
        refused();
        when(escrows.findByCampaignId(42L)).thenReturn(Optional.empty());
        refused();
    }

    private void refused() {
        assertThat(service.authorizeOverdue(42)).isFalse();
        verify(operations, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
        verify(campaign, never()).recordProofReview(any());
    }
}
