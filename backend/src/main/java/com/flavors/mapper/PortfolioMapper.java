package com.flavors.mapper;
 
import com.flavors.dto.PortfolioResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SocialAccount;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
 
/** Converts a creator's profile, accounts and campaigns into the Manage Portfolio response. */
@Component
public class PortfolioMapper {
 
    public PortfolioResponse toResponse(CreatorProfile profile, List<SocialAccount> accounts,
                                        List<Campaign> completedCampaigns, Set<Long> featuredCampaignIds) {
        return new PortfolioResponse(
                profile.getDisplayName(),
                profile.getBio(),
                profile.getFoodCategory(),
                profile.getLocation(),
                profile.getPriceMinCents(),
                profile.getPriceMaxCents(),
                accounts.stream()
                        .map(a -> new PortfolioResponse.SocialAccountView(
                                a.getPlatform(), a.getStatus(), a.getFollowers(), a.getEngagementRate(), a.getLastPulledAt()))
                        .toList(),
                completedCampaigns.stream()
                        .sorted(Comparator.comparing(Campaign::getId).reversed())
                        .map(c -> new PortfolioResponse.CampaignView(c.getId(), c.getName(), featuredCampaignIds.contains(c.getId())))
                        .toList());
    }
}