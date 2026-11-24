package com.flavors.service;

import com.flavors.dto.OfferRequest;
import com.flavors.dto.OfferResponse;
import java.util.Optional;

/**
 * The follower offer of a campaign (UC-F10.1). The brand is always the logged-in user; another brand's campaign is
 * "not found", like in the campaign endpoints.
 */
public interface OfferService {

    /** The campaign's offer, or empty if none has been set up yet. */
    Optional<OfferResponse> get(long brandId, long campaignId);

    /**
     * Sets up the offer, or replaces all its conditions if it exists (there is one per campaign). Allowed until the
     * campaign is active; refused fields are all named together and nothing is saved (EX-F10.1.3).
     */
    OfferResponse save(long brandId, long campaignId, OfferRequest request);
}
