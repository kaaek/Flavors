package com.flavors.enums;

/** Separate from escrow: only CONFIRMED means that the creator's payout was confirmed. */
public enum PayoutStatus {
    AUTHORIZED,
    UNKNOWN,
    SUBMITTED,
    FAILED,
    CONFIRMED
}
