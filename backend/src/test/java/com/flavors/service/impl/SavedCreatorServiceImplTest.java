package com.flavors.service.impl;
import com.flavors.entity.Brand;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SavedCreator;
import com.flavors.entity.SavedCreatorId;
import com.flavors.enums.VerificationStatus;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SavedCreatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class SavedCreatorServiceImplTest {
    @Mock
    private SavedCreatorRepository savedCreatorRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CreatorProfileRepository creatorProfileRepository;
    @InjectMocks
    private SavedCreatorServiceImpl service;
    private final Long brandId = 10L;
    private final Long creatorId = 5L;
    private CreatorProfile approvedCreator;
    @BeforeEach
    void setUp() {
        approvedCreator = mock(CreatorProfile.class);
    }
    @Test
    void shouldSaveApprovedCreator() {
        when(brandRepository.existsById(brandId))
                .thenReturn(true);
        when(creatorProfileRepository.findById(creatorId))
                .thenReturn(Optional.of(approvedCreator));
        when(approvedCreator.getVerificationStatus())
                .thenReturn(VerificationStatus.APPROVED);
        service.saveCreator(brandId, creatorId);
        verify(savedCreatorRepository).save(any(SavedCreator.class));
    }
    @Test
    void shouldNotSaveDuplicateCreator() {
        when(brandRepository.existsById(brandId))
                .thenReturn(true);
        when(creatorProfileRepository.findById(creatorId))
                .thenReturn(Optional.of(approvedCreator));
        when(approvedCreator.getVerificationStatus())
                .thenReturn(VerificationStatus.APPROVED);
        when(savedCreatorRepository.existsById(
                new SavedCreatorId(brandId, creatorId)))
                .thenReturn(true);
        service.saveCreator(brandId, creatorId);
        verify(savedCreatorRepository, never())
                .save(any(SavedCreator.class));
    }
    @Test
    void shouldRemoveSavedCreator() {
        SavedCreatorId id = new SavedCreatorId(brandId, creatorId);
        when(savedCreatorRepository.existsById(id))
                .thenReturn(true);
        service.removeCreator(brandId, creatorId);
        verify(savedCreatorRepository).deleteById(id);
    }
    @Test
    void shouldRejectUnapprovedCreator() {
        when(brandRepository.existsById(brandId))
                .thenReturn(true);
        when(creatorProfileRepository.findById(creatorId))
                .thenReturn(Optional.of(approvedCreator));
        when(approvedCreator.getVerificationStatus())
                .thenReturn(VerificationStatus.PENDING_APPROVAL);
        assertThrows(IllegalArgumentException.class,
                () -> service.saveCreator(brandId, creatorId));
        verify(savedCreatorRepository, never())
                .save(any(SavedCreator.class));
    }
}
