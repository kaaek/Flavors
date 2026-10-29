package com.flavors.dto;

import com.flavors.enums.Role;

/** Response of {@code GET /api/me}: who is logged in. */
public record MeResponse(long id, String email, Role role) {
}
