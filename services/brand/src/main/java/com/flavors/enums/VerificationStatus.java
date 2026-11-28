package com.flavors.enums;

/**
 * Where a creator is in the admin's approval. Owned by the creator service; this copy only lets the brand
 * service read the status of a creator it is asked to save (see {@code CreatorDirectory}).
 */
public enum VerificationStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
