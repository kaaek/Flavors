package com.flavors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The social account a creator connects while registering (UC-F19.1 step 3). */
public record SocialAccountRegistrationRequest(
        @NotBlank(message = "Social platform is required.")
        @Size(max = 30, message = "Platform must be at most 30 characters.")
        String platform,
        @NotBlank(message = "Social account is required.")
        @Size(max = 100, message = "Social account must be at most 100 characters.")
        String externalId) {
}
