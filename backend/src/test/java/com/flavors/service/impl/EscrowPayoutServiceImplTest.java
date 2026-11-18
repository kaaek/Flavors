package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.flavors.repository.*;
import com.flavors.service.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionOperations;

class EscrowPayoutServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private final EscrowReleaseOperationRepository operations = mock(EscrowReleaseOperationRepository.class);
    private final PayoutProvider provider = mock(PayoutProvider.class);
    private final EscrowPayoutServiceImpl service = spy(new EscrowPayoutServiceImpl(mock(CampaignRepository.class),
            mock(EscrowPaymentRepository.class), operations, mock(PaymentTransactionRepository.class),
            mock(ProofSubmissionRepository.class), mock(ProofReviewRepository.class), mock(AuditLogger.class),
            provider, mock(TransactionOperations.class), Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void unavailableProviderDoesNotClaimOrModifyAuthorizations() {
        assertThat(service.processPendingPayouts()).isZero();
        assertThat(service.processOperation(1)).isFalse();
        verifyNoInteractions(operations);
        verify(provider, never()).submit(any());
    }

    @Test
    void boundedBatchIsolatesAnOperationFailureAndContinues() {
        when(provider.available()).thenReturn(true);
        when(operations.findDuePayoutIds(NOW, PageRequest.of(0, 100))).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("rollback")).when(service).processOperation(1L);
        doReturn(true).when(service).processOperation(2L);
        doReturn(false).when(service).processOperation(3L);
        assertThat(service.processPendingPayouts()).isEqualTo(1);
        verify(service).processOperation(3L);
    }
}
