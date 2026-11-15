package com.flavors.repository;

import com.flavors.entity.EscrowPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowPaymentRepository extends JpaRepository<EscrowPayment, Long> {

    /** The campaign has at most one escrow (campaign_id is unique, V5). */
    Optional<EscrowPayment> findByCampaignId(Long campaignId);
}
