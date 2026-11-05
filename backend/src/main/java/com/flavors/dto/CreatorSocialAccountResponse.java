package com.flavors.dto;

import com.flavors.enums.SocialAccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** A connected account as the admin sees it; follower and engagement numbers come from the platform. */
public record CreatorSocialAccountResponse(String platform, String externalId, SocialAccountStatus status,
                                           Long followers, BigDecimal engagementRate, Instant lastPulledAt) {
}
