package com.flavors.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flavors.client.CreatorDirectory;
import com.flavors.entity.SavedCreatorId;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.CreatorNotApprovedException;
import com.flavors.exception.SavedCreatorNotFoundException;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.SavedCreatorRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Ported from the monolith; the creator profile lookup is now the {@link CreatorDirectory} cross-read. */
@ExtendWith(MockitoExtension.class)
class SavedCreatorServiceImplTest {

    @Mock
    private SavedCreatorRepository savedCreatorRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CreatorDirectory creators;
    @InjectMocks
    private SavedCreatorServiceImpl service;

    private final Long brandId = 10L;
    private final Long creatorId = 5L;

    @Test
    void shouldSaveApprovedCreator() {
        when(brandRepository.existsById(brandId)).thenReturn(true);
        when(creators.verificationStatus(creatorId)).thenReturn(Optional.of(VerificationStatus.APPROVED));

        service.saveCreator(brandId, creatorId);

        verify(savedCreatorRepository).saveIfAbsent(brandId, creatorId);
    }

    @Test
    void shouldRemoveSavedCreator() {
        SavedCreatorId id = new SavedCreatorId(brandId, creatorId);
        when(savedCreatorRepository.existsById(id)).thenReturn(true);

        service.removeCreator(brandId, creatorId);

        verify(savedCreatorRepository).deleteById(id);
    }

    @Test
    void shouldRejectUnapprovedCreator() {
        when(brandRepository.existsById(brandId)).thenReturn(true);
        when(creators.verificationStatus(creatorId)).thenReturn(Optional.of(VerificationStatus.PENDING_APPROVAL));

        assertThrows(CreatorNotApprovedException.class, () -> service.saveCreator(brandId, creatorId));

        verify(savedCreatorRepository, never()).saveIfAbsent(anyLong(), anyLong());
    }

    @Test
    void shouldRejectUnknownCreator() {
        when(brandRepository.existsById(brandId)).thenReturn(true);
        when(creators.verificationStatus(creatorId)).thenReturn(Optional.empty());

        assertThrows(SavedCreatorNotFoundException.class, () -> service.saveCreator(brandId, creatorId));

        verify(savedCreatorRepository, never()).saveIfAbsent(anyLong(), anyLong());
    }
}
