package com.flavors.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Everything a creator sends to register (FR-F19.1 to FR-F19.3). Prices are whole US cents. */
public record CreatorRegistrationRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be at most 100 characters.")
        String name,
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
        String password,
        @NotBlank(message = "Food category is required.")
        @Size(max = 50, message = "Food category must be at most 50 characters.")
        String foodCategory,
        @NotBlank(message = "Location is required.")
        @Size(max = 100, message = "Location must be at most 100 characters.")
        String location,
        @NotNull(message = "Minimum price is required.")
        @PositiveOrZero(message = "Minimum price can't be negative.")
        Long priceMinCents,
        @NotNull(message = "Maximum price is required.")
        @PositiveOrZero(message = "Maximum price can't be negative.")
        Long priceMaxCents,
        String bio,
        @NotEmpty(message = "Connect at least one social account.")
        List<@Valid SocialAccountRegistrationRequest> socialAccounts) {
}
