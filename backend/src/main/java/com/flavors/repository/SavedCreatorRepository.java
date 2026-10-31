package com.flavors.repository;
import com.flavors.entity.SavedCreator;
import com.flavors.entity.SavedCreatorId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedCreatorRepository
        extends JpaRepository<SavedCreator, SavedCreatorId> {
    List<SavedCreator> findByBrandIdOrderByCreatedAtDesc(Long brandId);
}
