package com.flavors.repository;

import com.flavors.entity.PaymentTransaction;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.enums.PaymentTransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByProviderRef(String providerRef);

    List<PaymentTransaction> findByEscrowIdAndTypeAndStatusOrderByIdDesc(Long escrowId, PaymentTransactionType type,
                                                                         PaymentTransactionStatus status);

    /** The campaign a payment reference belongs to. A scalar query, so the escrow row is not loaded. */
    @Query("select e.campaignId from EscrowPayment e, PaymentTransaction t "
            + "where t.escrowId = e.id and t.providerRef = :reference")
    Optional<Long> findCampaignIdByProviderRef(@Param("reference") String reference);

    /** Payments still PENDING on campaigns that are waiting for payment, created before {@code before}. */
    @Query("select t.providerRef from PaymentTransaction t, EscrowPayment e, Campaign c "
            + "where t.escrowId = e.id and e.campaignId = c.id and c.status = :campaignStatus "
            + "and t.status = :status and t.providerRef is not null and t.createdAt <= :before "
            + "order by t.id")
    List<String> findReferencesToReconcile(@Param("campaignStatus") CampaignStatus campaignStatus,
                                           @Param("status") PaymentTransactionStatus status,
                                           @Param("before") Instant before);
}
