package com.flavors.account;

/** Suspended users can't log in (FR-F19.12, FR-F21.2). Un-suspending is out of the MVP. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
