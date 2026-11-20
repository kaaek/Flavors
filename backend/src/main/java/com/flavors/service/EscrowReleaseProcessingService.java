package com.flavors.service;

/** Bounded database-only processing; payout execution is not implemented. */
public interface EscrowReleaseProcessingService {
    int authorizeOverdueBatch();
}
