package com.flavors.dto;

import com.flavors.enums.Role;

public record BrandRegistrationResponse(long id, String email, Role role) {
}
