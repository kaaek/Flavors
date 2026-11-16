package com.flavors.repository;

import com.flavors.entity.Offer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    /** A campaign has at most one offer (offer.campaign_id is unique, V4). */
    Optional<Offer> findByCampaignId(Long campaignId);

    /**
     * How many times the offer was used, to tell when it is full (FR-F10.4). Plain SQL: the redemption table gets its
     * entity with the redemption story (UC-F11.1), and a count is all this story needs.
     */
    @Query(value = "SELECT count(*) FROM redemption WHERE offer_id = :offerId", nativeQuery = true)
    long countRedemptions(@Param("offerId") Long offerId);
}
