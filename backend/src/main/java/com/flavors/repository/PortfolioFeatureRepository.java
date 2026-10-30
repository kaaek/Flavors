package com.flavors.repository;
 
import com.flavors.entity.PortfolioFeature;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
 
public interface PortfolioFeatureRepository extends JpaRepository<PortfolioFeature, Long> {
 
    /** The campaigns a creator features on her portfolio. */
    List<PortfolioFeature> findByCreatorId(Long creatorId);
 
    /** Clears a creator's featured list. Call it inside a transaction. */
    void deleteByCreatorId(Long creatorId);
}
 