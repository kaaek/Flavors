package com.flavors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectCreatorRequest(
        @NotBlank(message = "A reason is required to reject a profile.")
        @Size(max = 1000, message = "Reason must be at most 1000 characters.")
        String reason) {
}
