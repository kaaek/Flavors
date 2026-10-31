package com.flavors.repository;
 
import com.flavors.entity.Campaign;
import com.flavors.enums.CampaignStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
 
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
 
    /** E.g. the COMPLETED campaigns a creator can choose to feature. */
    List<Campaign> findByCreatorIdAndStatus(Long creatorId, CampaignStatus status);
}
 