package com.flavors.dto;

import com.flavors.enums.SocialAccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One of the creator's connected accounts, as the social-accounts page shows it. The id is what the
 * Disconnect button sends. The token is never part of it.
 */
public record SocialAccountResponse(
        long id,
        String platform,
        SocialAccountStatus status,
        Long followers,
        BigDecimal engagementRate,
        Instant lastPulledAt) {
}
