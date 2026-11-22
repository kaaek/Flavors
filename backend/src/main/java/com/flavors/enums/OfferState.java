package com.flavors.enums;

/**
 * Whether a follower offer can be used right now (FR-F10.4). Not stored: worked out from the campaign's status, the
 * expiry and the number of redemptions every time the offer is read, so it can never be out of date.
 */
public enum OfferState {
    /** The campaign hasn't started yet (draft, waiting for the creator, or waiting for payment). */
    NOT_LIVE_YET,
    /** The campaign is active and the offer can be used. Only {@code ACTIVE} counts (FR-F10.4). */
    LIVE,
    /** The creator's proof is being reviewed or corrected: the posting is done, so followers can't use it any more. */
    IN_REVIEW,
    /** The campaign is disputed: the offer is held until the dispute is decided. */
    PAUSED,
    /** The expiry date has passed. */
    EXPIRED,
    /** The maximum number of redemptions has been reached. */
    FULL,
    /** The campaign is over (completed, cancelled, removed) or never happened (rejected, declined, expired). */
    ENDED
}
