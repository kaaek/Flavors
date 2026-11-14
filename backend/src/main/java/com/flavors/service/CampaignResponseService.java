package com.flavors.service;

import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.CounterOfferResponse;

public interface CampaignResponseService {

    void acceptCampaign(Long creatorId, Long campaignId);

    void rejectCampaign(Long creatorId, Long campaignId);

    void submitCounterOffer(
            Long creatorId,
            Long campaignId,
            CounterOfferRequest request);

    CounterOfferResponse getCounterOfferForCreator(
            Long creatorId, Long campaignId);

    CounterOfferResponse getCounterOfferForBrand(
            Long brandId, Long campaignId);

    void approveCounterOffer(Long brandId, Long campaignId);

    void declineCounterOffer(Long brandId, Long campaignId);

    int expireOverdueCampaignResponses();
}