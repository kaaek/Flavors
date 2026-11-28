package com.flavors.platform.security;

/** The three account types, same as the monolith's {@code Role}. */
public enum Role {
    CREATOR,
    BRAND,
    ADMIN;

    /** Spring Security's name for the role; {@code hasRole("BRAND")} checks for {@code ROLE_BRAND}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
