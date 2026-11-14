
package com.flavors.entity;

import com.flavors.enums.CampaignStatus;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Creates Campaign objects for unit tests without changing production code.
 */
public final class CampaignTestFactory {

    private CampaignTestFactory() {
    }

    public static Campaign awaitingCreatorResponse(
            Long campaignId,
            Long brandId,
            Long creatorId,
            String name,
            Long priceOfferedCents,
            Instant responseDeadline) {

        Campaign campaign = new Campaign();

        ReflectionTestUtils.setField(campaign, "id", campaignId);
        ReflectionTestUtils.setField(campaign, "brandId", brandId);
        ReflectionTestUtils.setField(campaign, "creatorId", creatorId);
        ReflectionTestUtils.setField(campaign, "name", name);
        ReflectionTestUtils.setField(
                campaign, "status", CampaignStatus.AWAITING_CREATOR_RESPONSE);
        ReflectionTestUtils.setField(
                campaign, "priceOfferedCents", priceOfferedCents);
        ReflectionTestUtils.setField(
                campaign, "responseDeadline", responseDeadline);

        return campaign;
    }

    public static Campaign countered(
            Long campaignId,
            Long brandId,
            Long creatorId,
            String name,
            Long priceOfferedCents) {

        Campaign campaign = awaitingCreatorResponse(
                campaignId,
                brandId,
                creatorId,
                name,
                priceOfferedCents,
                Instant.parse("2026-11-10T12:00:00Z"));

        ReflectionTestUtils.setField(
                campaign, "status", CampaignStatus.COUNTERED);

        return campaign;
    }
}
