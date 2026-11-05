package com.flavors.dto;

import com.flavors.enums.Role;
import com.flavors.enums.VerificationStatus;

public record CreatorRegistrationResponse(long id, String email, Role role, VerificationStatus verificationStatus) {
}
