package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flavors.dto.ProofDraftRequest;
import com.flavors.entity.Campaign;
import com.flavors.entity.ProofFile;
import com.flavors.entity.ProofLink;
import com.flavors.entity.ProofSubmission;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.ProofFileRepository;
import com.flavors.repository.ProofLinkRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.ProofFileStorage;
import com.flavors.service.ProofUpload;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Exercises business rules and storage completion callbacks without starting Docker or a database. */
class DeliveryProofServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final ProofSubmissionRepository submissions = mock(ProofSubmissionRepository.class);
    private final ProofLinkRepository links = mock(ProofLinkRepository.class);
    private final ProofFileRepository files = mock(ProofFileRepository.class);
    private final ProofFileStorage storage = mock(ProofFileStorage.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final DeliveryProofServiceImpl service = new DeliveryProofServiceImpl(campaigns, submissions, links,
            files, storage, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    private Campaign campaign;
    private ProofSubmission draft;

    @BeforeEach
    void setUp() {
        campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(42L);
        when(campaign.getCreatorId()).thenReturn(7L);
        when(campaign.getBrandId()).thenReturn(9L);
        when(campaign.getStatus()).thenReturn(CampaignStatus.ACTIVE);
        when(campaign.currentProofRound()).thenReturn(1);
        when(campaigns.lockById(42L)).thenReturn(Optional.of(campaign));
        when(campaigns.findById(42L)).thenReturn(Optional.of(campaign));
        draft = new ProofSubmission(42, 1);
        ReflectionTestUtils.setField(draft, "id", 5L);
        when(submissions.findByCampaignIdAndStatus(42L, ProofSubmissionStatus.DRAFT))
                .thenReturn(Optional.of(draft));
        when(submissions.findByCampaignIdOrderByRoundAsc(42L)).thenReturn(List.of(draft));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionCallbacks() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void finalSubmissionUsesTheSubmissionTimeForTheFiveDayDeadline() {
        when(links.findBySubmissionIdOrderById(5L)).thenReturn(List.of(new ProofLink(5, "https://example.com/post")));

        service.submit(7, 42, null, List.of());

        assertThat(draft.getStatus()).isEqualTo(ProofSubmissionStatus.SUBMITTED);
        assertThat(draft.getSubmittedAt()).isEqualTo(NOW);
        verify(campaign).markProofSubmitted(NOW, NOW.plus(Duration.ofDays(5)));
    }

    @Test
    void submittedCampaignCannotBeEditedOrSentAgain() {
        when(campaign.getStatus()).thenReturn(CampaignStatus.PROOF_SUBMITTED);

        assertThatThrownBy(() -> service.saveDraft(7, 42, null, List.of()))
                .isInstanceOf(DeliveryProofConflictException.class);
        assertThatThrownBy(() -> service.submit(7, 42, null, List.of()))
                .isInstanceOf(DeliveryProofConflictException.class);
        verifyNoInteractions(storage, submissions, links, files, audit);
    }

    @Test
    void aDraftCannotContainMoreThanTenLinks() {
        List<String> eleven = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "https://example.com/post-" + i)
                .toList();

        assertThatThrownBy(() -> service.saveDraft(7, 42, new ProofDraftRequest(eleven, null), List.of()))
                .isInstanceOf(InvalidDeliveryProofException.class)
                .hasMessageContaining("at most 10 links");
        verify(links, never()).saveAll(any());
        verify(links, never()).deleteAll(any());
    }

    @Test
    void draftScreenshotsAreHiddenEvenFromTheBrandAndAdmin() {
        ProofFile file = existingFile();
        when(files.findById(8L)).thenReturn(Optional.of(file));
        when(submissions.findById(5L)).thenReturn(Optional.of(draft));

        assertThat(service.getForBrand(9, 42).submissions()).isEmpty();
        assertThat(service.getForAdmin(42).submissions()).isEmpty();
        assertThatThrownBy(() -> service.download(9, Role.BRAND, 8))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> service.download(11, Role.ADMIN, 8))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rollbackRemovesTheNewUploadButPreservesTheOldDraftObject() {
        String uploaded = replaceDraftImage();

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(uploaded);
        verify(storage, never()).delete("proof/old.png");
    }

    @Test
    void commitRemovesTheOldDraftObjectButPreservesTheNewUpload() {
        String uploaded = replaceDraftImage();

        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage).delete("proof/old.png");
        verify(storage, never()).delete(uploaded);
    }

    @Test
    void unknownTransactionOutcomeNeverDeletesPotentiallyCommittedEvidence() {
        replaceDraftImage();

        complete(TransactionSynchronization.STATUS_UNKNOWN);

        verify(storage, never()).delete(anyString());
    }

    private String replaceDraftImage() {
        when(files.findBySubmissionIdOrderById(5L)).thenReturn(List.of(existingFile()));
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        service.saveDraft(7, 42, new ProofDraftRequest(null, List.of()),
                List.of(new ProofUpload("new.png", "image/png", png)));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(byte[].class), eq("image/png"));
        // Objects are not deleted before the database outcome is known.
        verify(storage, never()).delete(anyString());
        return key.getValue();
    }

    private static ProofFile existingFile() {
        ProofFile file = new ProofFile(5, "proof/old.png", "image/png", 8);
        ReflectionTestUtils.setField(file, "id", 8L);
        return file;
    }

    private static void complete(int outcome) {
        TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCompletion(outcome));
    }
}
