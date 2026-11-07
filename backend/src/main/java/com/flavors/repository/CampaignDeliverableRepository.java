package com.flavors.repository;

import com.flavors.entity.CampaignDeliverable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignDeliverableRepository extends JpaRepository<CampaignDeliverable, Long> {

    List<CampaignDeliverable> findByCampaignId(Long campaignId);

    void deleteByCampaignId(Long campaignId);
}
