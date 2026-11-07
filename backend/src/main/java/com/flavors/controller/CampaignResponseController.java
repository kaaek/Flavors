
package com.flavors.controller;

import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.CurrentUser;
import com.flavors.service.CampaignResponseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CampaignResponseController {

    private final CampaignResponseService campaignResponseService;

    public CampaignResponseController(
            CampaignResponseService campaignResponseService) {
        this.campaignResponseService = campaignResponseService;
    }

    @PostMapping("/api/creator/campaigns/{campaignId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptCampaign(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long campaignId) {
        campaignResponseService.acceptCampaign(user.getId(), campaignId);
    }

    @PostMapping("/api/creator/campaigns/{campaignId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectCampaign(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long campaignId) {
        campaignResponseService.rejectCampaign(user.getId(), campaignId);
    }

    @PostMapping("/api/creator/campaigns/{campaignId}/counter-offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitCounterOffer(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long campaignId,
            @Valid @RequestBody CounterOfferRequest request) {
        campaignResponseService.submitCounterOffer(
                user.getId(), campaignId, request);
    }

    @PostMapping("/api/brand/campaigns/{campaignId}/counter-offer/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveCounterOffer(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long campaignId) {
        campaignResponseService.approveCounterOffer(
                user.getId(), campaignId);
    }

    @PostMapping("/api/brand/campaigns/{campaignId}/counter-offer/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineCounterOffer(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long campaignId) {
        campaignResponseService.declineCounterOffer(
                user.getId(), campaignId);
    }
}
