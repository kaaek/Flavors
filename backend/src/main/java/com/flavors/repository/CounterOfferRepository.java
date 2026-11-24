package com.flavors.repository;

import com.flavors.entity.CounterOffer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterOfferRepository extends JpaRepository<CounterOffer, Long> {

    Optional<CounterOffer> findByCampaignId(Long campaignId);

    boolean existsByCampaignId(Long campaignId);
}
