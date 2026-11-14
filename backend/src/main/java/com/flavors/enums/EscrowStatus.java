package com.flavors.enums;

/** The escrow states (the status check of escrow_payment, V5). Only PENDING and HELD are reached by EEC-44. */
public enum EscrowStatus {
    PENDING,
    HELD,
    RELEASED,
    FROZEN,
    REFUNDED
}
