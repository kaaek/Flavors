package com.flavors.service;

import com.flavors.entity.Review;

public interface CampaignReviewService {
    Review submit(long brandId, long campaignId, short rating, String text);
    Review get(long brandId, long campaignId);
}
