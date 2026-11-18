package com.flavors.repository;

import com.flavors.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Every review of a creator's campaigns, newest first. */
    @Query("""
            select r from Review r, Campaign c
            where r.campaignId = c.id and c.creatorId = :creatorId
            order by r.createdAt desc
            """)
    List<Review> findAllForCreator(@Param("creatorId") Long creatorId);
    boolean existsByCampaignId(Long campaignId);
    Optional<Review> findByCampaignId(Long campaignId);
}
