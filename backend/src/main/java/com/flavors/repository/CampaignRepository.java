package com.flavors.repository;

import com.flavors.entity.Campaign;
import com.flavors.enums.CampaignStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** E.g. the COMPLETED campaigns a creator can choose to feature. */
    List<Campaign> findByCreatorIdAndStatus(Long creatorId, CampaignStatus status);

    /** Serializes draft saves and final submission for one campaign. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> lockById(@Param("id") Long id);
}
