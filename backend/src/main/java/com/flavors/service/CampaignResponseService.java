
package com.flavors.service;

import com.flavors.dto.CounterOfferRequest;

public interface CampaignResponseService {

    void acceptCampaign(Long creatorId, Long campaignId);

    void rejectCampaign(Long creatorId, Long campaignId);

    void submitCounterOffer(
            Long creatorId,
            Long campaignId,
            CounterOfferRequest request);

    void approveCounterOffer(Long brandId, Long campaignId);

    void declineCounterOffer(Long brandId, Long campaignId);
    int expireOverdueCampaignResponses();
}
