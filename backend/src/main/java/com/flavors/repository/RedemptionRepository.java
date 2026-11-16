package com.flavors.repository;

import com.flavors.entity.Redemption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    /** What a campaign's redemptions add up to. */
    interface CampaignTotals {

        Long getCampaignId();

        Long getRedemptions();

        /** The bills, before the discount. */
        Long getRevenueCents();

        Long getDiscountCents();
    }

    /** One row per campaign that has at least one redemption; campaigns without any are not returned. */
    @Query("""
            select r.campaignId as campaignId, count(r) as redemptions,
                   sum(r.billCents) as revenueCents, sum(r.discountCents) as discountCents
            from Redemption r
            where r.campaignId in :campaignIds
            group by r.campaignId
            """)
    List<CampaignTotals> totalsByCampaign(@Param("campaignIds") Collection<Long> campaignIds);
}
