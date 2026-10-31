package com.flavors.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BrandRegistrationRequest(
        @NotBlank(message = "Business name is required.")
        @Size(max = 150, message = "Business name must be at most 150 characters.")
        String businessName,
        @NotBlank(message = "Contact person is required.")
        @Size(max = 100, message = "Contact person must be at most 100 characters.")
        String contactPerson,
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,
        @NotBlank(message = "Phone number is required.")
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{5,28}$", message = "Enter a valid phone number.")
        String phone,
        @NotBlank(message = "Password is required.")
        String password,
        @NotEmpty(message = "Add at least one branch.")
        List<@Valid BranchRegistrationRequest> branches) {
}
