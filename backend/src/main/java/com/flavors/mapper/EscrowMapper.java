package com.flavors.mapper;

import com.flavors.dto.EscrowResponse;
import com.flavors.entity.EscrowPayment;
import com.flavors.entity.EscrowReleaseOperation;
import org.springframework.stereotype.Component;

/** Public read model only: no provider references, destinations, leases, counters, or error details. */
@Component
public class EscrowMapper {
    public EscrowResponse toResponse(EscrowPayment escrow, EscrowReleaseOperation operation,
                                    boolean payoutAvailable, boolean configuredSimulation) {
        Boolean simulated = operation == null ? null : operation.getPayoutProvider() == null
                ? configuredSimulation : "SIMULATED".equals(operation.getPayoutProvider());
        return new EscrowResponse(escrow.getCampaignId(), escrow.getStatus(), escrow.getAmountCents(),
                escrow.getHeldAt(), escrow.getReleasedAt(), escrow.getUpdatedAt(),
                operation == null ? null : operation.getFeeCents(),
                operation == null ? null : operation.getCreatorNetCents(),
                operation == null ? null : operation.getStatus(),
                operation == null ? null : operation.getAuthorizationSource(),
                operation == null ? null : operation.getAuthorizedAt(),
                operation == null ? null : operation.getSubmittedAt(),
                operation == null ? null : operation.getConfirmedAt(), simulated, payoutAvailable);
    }
}
