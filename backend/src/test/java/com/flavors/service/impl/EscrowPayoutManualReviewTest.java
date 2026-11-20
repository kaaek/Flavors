
package com.flavors.service.impl;

import com.flavors.dto.CurrentUser;
import com.flavors.entity.Campaign;
import com.flavors.entity.EscrowPayment;
import com.flavors.entity.EscrowReleaseOperation;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.Role;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.EscrowPaymentRepository;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.repository.PaymentTransactionRepository;
import com.flavors.repository.ProofReviewRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.PayoutProvider;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EscrowPayoutManualReviewTest {

    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final EscrowPaymentRepository escrows = mock(EscrowPaymentRepository.class);
    private final EscrowReleaseOperationRepository operations =
            mock(EscrowReleaseOperationRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private final EscrowPayoutServiceImpl service =
            new EscrowPayoutServiceImpl(
                    campaigns,
                    escrows,
                    operations,
                    mock(PaymentTransactionRepository.class),
                    mock(ProofSubmissionRepository.class),
                    mock(ProofReviewRepository.class),
                    audit,
                    mock(PayoutProvider.class),
                    mock(TransactionOperations.class)
            );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Role role) {
        CurrentUser user = new CurrentUser(
                7L, "reviewer@example.com", role, "unused", true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()));
    }

    private void eligiblePayout() {
        EscrowReleaseOperation operation = mock(EscrowReleaseOperation.class);
        EscrowPayment escrow = mock(EscrowPayment.class);
        Campaign campaign = mock(Campaign.class);

        when(operation.getId()).thenReturn(42L);
        when(operation.getEscrowId()).thenReturn(10L);
        when(operation.getNeedsReviewAt())
                .thenReturn(Instant.parse("2026-11-20T00:00:00Z"));
        when(operation.getStatus()).thenReturn(PayoutStatus.UNKNOWN);

        when(escrow.getId()).thenReturn(10L);
        when(escrow.getCampaignId()).thenReturn(20L);
        when(escrow.getStatus()).thenReturn(EscrowStatus.HELD);

        when(campaign.getId()).thenReturn(20L);

        when(operations.findById(42L)).thenReturn(Optional.of(operation));
        when(escrows.findById(10L)).thenReturn(Optional.of(escrow));
        when(campaigns.findByIdForUpdate(20L))
                .thenReturn(Optional.of(campaign));
        when(operations.findByEscrowIdForUpdate(10L))
                .thenReturn(Optional.of(operation));
    }

    @Test
    void administratorCanRecordEvidence() {
        authenticate(Role.ADMIN);
        eligiblePayout();

        service.recordManualReviewEvidence(
                42L, "provider-case-123", "Provider investigation recorded");

        verify(audit).record(
                eq("PAYOUT_MANUAL_REVIEW_EVIDENCE"),
                eq("escrow_release_operation"),
                eq(42L),
                eq(Map.of(
                        "campaignId", 20L,
                        "operationId", 42L,
                        "escrowId", 10L,
                        "evidenceReference", "provider-case-123",
                        "finding", "Provider investigation recorded"
                ))
        );
    }

    @Test
    void brandCannotRecordEvidence() {
        authenticate(Role.BRAND);

        assertThatThrownBy(() -> service.recordManualReviewEvidence(
                42L, "provider-case-123", "Investigation recorded"))
                .isInstanceOf(
                        org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(audit);
    }

    @Test
    void blankEvidenceIsRejected() {
        authenticate(Role.ADMIN);

        assertThatThrownBy(() -> service.recordManualReviewEvidence(
                42L, " ", "Investigation recorded"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(audit);
    }

    @Test
    void payoutNotFlaggedForReviewIsRejected() {
        authenticate(Role.ADMIN);
        eligiblePayout();

        EscrowReleaseOperation operation =
                operations.findById(42L).orElseThrow();

        when(operation.getNeedsReviewAt()).thenReturn(null);

        assertThatThrownBy(() -> service.recordManualReviewEvidence(
                42L, "provider-case-123", "Investigation recorded"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(audit);
    }
}
