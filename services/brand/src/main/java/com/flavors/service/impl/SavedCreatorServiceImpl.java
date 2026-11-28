package com.flavors.service.impl;
import com.flavors.client.CreatorDirectory;
import com.flavors.entity.SavedCreator;
import com.flavors.entity.SavedCreatorId;
import com.flavors.enums.VerificationStatus;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.SavedCreatorRepository;
import com.flavors.service.SavedCreatorService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.flavors.exception.SavedCreatorNotFoundException;
import com.flavors.exception.CreatorNotApprovedException;
@Service
public class SavedCreatorServiceImpl implements SavedCreatorService {
    private final SavedCreatorRepository savedCreatorRepository;
    private final BrandRepository brandRepository;
    // Creators belong to the creator service; this is a read-only cross-read (see CreatorDirectory).
    private final CreatorDirectory creators;
    public SavedCreatorServiceImpl(
            SavedCreatorRepository savedCreatorRepository,
            BrandRepository brandRepository,
            CreatorDirectory creators) {
        this.savedCreatorRepository = savedCreatorRepository;
        this.brandRepository = brandRepository;
        this.creators = creators;
    }
    @Override
    @Transactional
    public void saveCreator(Long brandId, Long creatorId) {
        if (!brandRepository.existsById(brandId)) {
            throw new SavedCreatorNotFoundException("Cannot save creator: brand account not found");
        }
        var status = creators.verificationStatus(creatorId)
                .orElseThrow(() ->
                        new SavedCreatorNotFoundException("Creator not found"));
        if (status != VerificationStatus.APPROVED) {
            throw new CreatorNotApprovedException();
        }
        savedCreatorRepository.saveIfAbsent(brandId, creatorId);
    }

    @Override
    @Transactional
    public void removeCreator(Long brandId, Long creatorId) {
        SavedCreatorId id = new SavedCreatorId(brandId, creatorId);
        if (savedCreatorRepository.existsById(id)) {
            savedCreatorRepository.deleteById(id);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public List<SavedCreator> getSavedCreators(Long brandId) {
        return savedCreatorRepository.findByBrandIdOrderByCreatedAtDesc(brandId);
    }
}
