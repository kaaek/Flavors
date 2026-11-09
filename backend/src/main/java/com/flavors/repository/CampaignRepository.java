package com.flavors.repository;

import com.flavors.entity.Campaign;
import com.flavors.enums.CampaignStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** E.g. the COMPLETED campaigns a creator can choose to feature. */
    List<Campaign> findByCreatorIdAndStatus(Long creatorId, CampaignStatus status);

    /** The brand's own campaigns, most recently changed first. */
    List<Campaign> findByBrandIdOrderByUpdatedAtDescIdDesc(Long brandId);

    /** Filtering by brand in the query means another brand's campaign is simply not found. */
    Optional<Campaign> findByIdAndBrandId(Long id, Long brandId);

    /**
     * Same, and locks the row until the transaction ends, so two requests sending or editing the same
     * campaign at once run one after the other and only the first one sends (and notifies).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id and c.brandId = :brandId")
    Optional<Campaign> findByIdAndBrandIdForUpdate(@Param("id") Long id, @Param("brandId") Long brandId);
}
