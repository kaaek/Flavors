
package com.flavors.controller;

import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CreatorCampaignSummaryResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.service.CampaignService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creator/campaigns")
public class CreatorCampaignController {

    private final CampaignService campaigns;

    public CreatorCampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping
    public List<CreatorCampaignSummaryResponse> list(
            @AuthenticationPrincipal CurrentUser user) {
        return campaigns.listForCreator(user.getId());
    }

    @GetMapping("/{campaignId}")
    public CampaignResponse get(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long campaignId) {
        return campaigns.getForCreator(user.getId(), campaignId);
    }
}
