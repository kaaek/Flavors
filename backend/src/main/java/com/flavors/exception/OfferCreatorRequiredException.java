package com.flavors.exception;

/**
 * The campaign has no creator yet (a draft may be saved without one), and an offer is linked to the campaign's
 * creator (FR-F10.1): the brand has to choose the creator first. Maps to HTTP 409.
 */
public class OfferCreatorRequiredException extends RuntimeException {

    public OfferCreatorRequiredException() {
        super("Choose the campaign's creator first: the offer is linked to the creator whose followers get it. "
                + "Choose one on the campaign, save the draft, then set up the offer.");
    }
}
