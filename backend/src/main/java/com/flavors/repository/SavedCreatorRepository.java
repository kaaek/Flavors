package com.flavors.repository;
import com.flavors.entity.SavedCreator;
import com.flavors.entity.SavedCreatorId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface SavedCreatorRepository
        extends JpaRepository<SavedCreator, SavedCreatorId> {
    List<SavedCreator> findByBrandIdOrderByCreatedAtDesc(Long brandId);
    @Modifying
    @Query(value = """
            INSERT INTO saved_creator (brand_id, creator_id)
            VALUES (:brandId, :creatorId)
            ON CONFLICT (brand_id, creator_id) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(
            @Param("brandId") Long brandId,
            @Param("creatorId") Long creatorId);
}
