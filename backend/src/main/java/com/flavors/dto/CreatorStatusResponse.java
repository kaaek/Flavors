package com.flavors.dto;

import com.flavors.enums.VerificationStatus;

/** What a creator sees about her own approval (FR-F20.5). The reason is set only after a rejection. */
public record CreatorStatusResponse(VerificationStatus verificationStatus, String rejectionReason) {
}
