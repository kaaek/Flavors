package com.flavors.dto;

import com.flavors.enums.VerificationStatus;
import java.time.Instant;
import java.util.List;

/**
 * A creator's registration details and social numbers for the admin (FR-F20.2).
 * {@code needsReconnection} is true when approval is blocked (EX-F20.1.2).
 */
public record CreatorReviewResponse(long id, String email, String displayName, String bio, String foodCategory,
                                    String location, long priceMinCents, long priceMaxCents,
                                    VerificationStatus verificationStatus, String rejectionReason,
                                    boolean needsReconnection, Instant registeredAt, Instant reviewedAt,
                                    List<CreatorSocialAccountResponse> socialAccounts) {
}
