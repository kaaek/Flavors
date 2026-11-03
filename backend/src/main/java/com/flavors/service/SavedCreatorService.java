package com.flavors.service;
import com.flavors.entity.SavedCreator;
import java.util.List;
public interface SavedCreatorService {
    void saveCreator(Long brandId, Long creatorId);
    void removeCreator(Long brandId, Long creatorId);
    List<SavedCreator> getSavedCreators(Long brandId);
}
