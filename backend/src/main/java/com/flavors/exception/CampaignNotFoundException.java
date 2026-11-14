package com.flavors.exception;

/** No such campaign, or it belongs to another brand: the two look the same, so ids can't be probed. */
public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException() {
        super("Campaign not found.");
    }
}
