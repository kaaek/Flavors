
package com.flavors.repository;

import com.flavors.entity.EscrowReleaseOperation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscrowReleaseOperationRepository
        extends JpaRepository<EscrowReleaseOperation, Long> {

    Optional<EscrowReleaseOperation> findByEscrowId(Long escrowId);

    Optional<EscrowReleaseOperation> findByOperationKey(String operationKey);

    Optional<EscrowReleaseOperation> findByPayoutProviderAndProviderRef(
            String payoutProvider, String providerRef);

    /**
     * Find payout operations that need processing.
     * Pending and unknown payouts remain eligible for reconciliation,
     * regardless of how many queries have already been attempted.
     */
    @Query("""
            select o.id from EscrowReleaseOperation o, EscrowPayment e, Campaign c
            where o.escrowId = e.id and e.campaignId = c.id
              and e.status = com.flavors.enums.EscrowStatus.HELD
              and c.status = com.flavors.enums.CampaignStatus.PROOF_SUBMITTED
              and (
                  o.status in (
                      com.flavors.enums.PayoutStatus.AUTHORIZED,
                      com.flavors.enums.PayoutStatus.UNKNOWN,
                      com.flavors.enums.PayoutStatus.SUBMITTED
                  )
                  or (
                      o.status = com.flavors.enums.PayoutStatus.FAILED
                      and o.retryable = true
                  )
              )
              and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)
              and (o.leaseUntil is null or o.leaseUntil <= :now)
            order by coalesce(o.nextAttemptAt, o.authorizedAt), o.id
            """)
    List<Long> findDuePayoutIds(
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * Lock the operation after locking its campaign.
     * Shared lock order: campaign -> operation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from EscrowReleaseOperation o
            where o.escrowId = :escrowId
            """)
    Optional<EscrowReleaseOperation> findByEscrowIdForUpdate(
            @Param("escrowId") Long escrowId);
}
