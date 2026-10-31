package com.flavors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchRegistrationRequest(
        @NotBlank(message = "Branch name is required.")
        @Size(max = 100, message = "Branch name must be at most 100 characters.")
        String name,
        @NotBlank(message = "Branch location is required.")
        @Size(max = 200, message = "Branch location must be at most 200 characters.")
        String location) {
}
