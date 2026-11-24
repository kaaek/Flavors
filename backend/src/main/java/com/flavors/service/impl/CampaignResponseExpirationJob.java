package com.flavors.service.impl;

import com.flavors.service.CampaignResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignResponseExpirationJob {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CampaignResponseExpirationJob.class);

    private final CampaignResponseService campaignResponseService;

    public CampaignResponseExpirationJob(
            CampaignResponseService campaignResponseService) {
        this.campaignResponseService = campaignResponseService;
    }

    @Scheduled(
            cron = "${flavors.campaign.response-expiration-cron:0 * * * * *}",
            zone = "${flavors.campaign.response-expiration-zone:Asia/Beirut}")
    public void expireOverdueResponses() {
        int expiredCount =
                campaignResponseService.expireOverdueCampaignResponses();

        if (expiredCount > 0) {
            LOGGER.info(
                    "Campaign response expiration completed: {} campaigns expired.",
                    expiredCount);
        }
    }
}