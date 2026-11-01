package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.FeatureCampaignRequest;
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.service.PortfolioService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The creator's Manage Portfolio page (UC-F1.2). Only creators reach {@code /api/creator/**}
 * (SecurityConfig), and the creator is always the logged-in user, so she can only touch her own portfolio.
 *
 * <p>Details are read, replaced (PUT) or partly edited (PATCH). Featured campaigns are added (POST) and
 * removed (DELETE) one at a time, so no edit of the details can ever remove a feature.
 */
@RestController
@RequestMapping("/api/creator/portfolio")
class PortfolioController {

    private final PortfolioService service;

    PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping
    PortfolioResponse get(@AuthenticationPrincipal CurrentUser user) {
        return service.getMyPortfolio(user.getId());
    }

    @PutMapping
    PortfolioResponse replace(@AuthenticationPrincipal CurrentUser user, @RequestBody UpdatePortfolioRequest request) {
        return service.updateMyPortfolio(user.getId(), request);
    }

    /** Bound to a map, so the service can tell a field that was left out from one sent as null. */
    @PatchMapping
    PortfolioResponse patch(@AuthenticationPrincipal CurrentUser user, @RequestBody Map<String, Object> changes) {
        return service.patchMyPortfolio(user.getId(), changes);
    }

    @PostMapping("/featured")
    @ResponseStatus(HttpStatus.CREATED)
    PortfolioResponse.CampaignView feature(@AuthenticationPrincipal CurrentUser user,
                                           @RequestBody FeatureCampaignRequest request) {
        return service.featureCampaign(user.getId(), request.campaignId());
    }

    @DeleteMapping("/featured/{campaignId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfeature(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId) {
        service.unfeatureCampaign(user.getId(), campaignId);
    }
}