package com.flavors.enums;

/**
 * The three account types. Diners and cashiers have no account: diner pages are public, and the
 * cashier's scan page runs in the restaurant's BRAND session.
 */
public enum Role {
    CREATOR,
    BRAND,
    ADMIN;

    /** Spring Security's name for the role; {@code hasRole("BRAND")} checks for {@code ROLE_BRAND}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
