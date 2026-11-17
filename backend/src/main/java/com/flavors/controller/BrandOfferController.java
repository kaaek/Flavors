package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.OfferRequest;
import com.flavors.dto.OfferResponse;
import com.flavors.service.OfferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The follower offer of one of the brand's campaigns (UC-F10.1). Under {@code /api/brand/**}, so only the BRAND role
 * gets in, and the brand is always the logged-in user: another brand's campaign is "not found" (404).
 */
@RestController
@RequestMapping("/api/brand/campaigns/{campaignId}/offer")
public class BrandOfferController {

    private final OfferService offers;

    public BrandOfferController(OfferService offers) {
        this.offers = offers;
    }

    /** The offer, or 204 when none has been set up yet: the page then shows an empty form. */
    @GetMapping
    ResponseEntity<OfferResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId) {
        return offers.get(user.getId(), campaignId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Sets up the offer, or replaces its conditions: there is one per campaign, so the same call does both. */
    @PutMapping
    OfferResponse save(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId,
                       @RequestBody OfferRequest request) {
        return offers.save(user.getId(), campaignId, request);
    }
}
