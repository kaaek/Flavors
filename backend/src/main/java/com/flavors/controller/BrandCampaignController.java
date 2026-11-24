package com.flavors.controller;

import com.flavors.dto.CampaignCreatorResponse;
import com.flavors.dto.CampaignRequest;
import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CampaignSummaryResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.service.CampaignService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The brand's campaigns (UC-F3.1). Under {@code /api/brand/**}, so only the BRAND role gets in; the brand is
 * always the logged-in user, so a brand never reaches another brand's campaign. A creator has no way to read a
 * draft: there is no creator-side endpoint, and the creator is told only when the brand sends.
 */
@RestController
@RequestMapping("/api/brand/campaigns")
public class BrandCampaignController {

    private final CampaignService campaigns;

    public BrandCampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping
    List<CampaignSummaryResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return campaigns.list(user.getId());
    }

    @GetMapping("/{id}")
    CampaignResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return campaigns.get(user.getId(), id);
    }

    /** The creator the form opens with after "Start a campaign". */
    @GetMapping("/creators/{creatorId}")
    CampaignCreatorResponse creator(@PathVariable long creatorId) {
        return campaigns.findCreator(creatorId);
    }

    /** Saves a new draft. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CampaignResponse saveDraft(@AuthenticationPrincipal CurrentUser user, @RequestBody CampaignRequest request) {
        return campaigns.saveDraft(user.getId(), request);
    }

    /** Replaces the details of a draft. */
    @PutMapping("/{id}")
    CampaignResponse updateDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                                 @RequestBody CampaignRequest request) {
        return campaigns.updateDraft(user.getId(), id, request);
    }

    /** Creates a campaign and sends it to the creator in one step. */
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    CampaignResponse send(@AuthenticationPrincipal CurrentUser user, @RequestBody CampaignRequest request) {
        return campaigns.send(user.getId(), request);
    }

    /** Saves the given details into a draft and sends it to the creator. */
    @PostMapping("/{id}/send")
    CampaignResponse send(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                          @RequestBody CampaignRequest request) {
        return campaigns.send(user.getId(), id, request);
    }
}
