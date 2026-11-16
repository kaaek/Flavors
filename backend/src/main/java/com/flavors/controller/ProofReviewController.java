package com.flavors.controller;

import com.flavors.dto.CampaignProofReviewsResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.dto.ProofReviewResponse;
import com.flavors.service.ProofReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-F6.1. Role prefixes are enforced by SecurityConfig; ownership is checked by the service. */
@RestController
@RequestMapping("/api")
public class ProofReviewController {

    private final ProofReviewService reviews;

    public ProofReviewController(ProofReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/brand/campaigns/{campaignId}/proof/{submissionId}/review")
    ProofReviewResponse review(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId,
                              @PathVariable long submissionId, @Valid @RequestBody ProofReviewRequest request) {
        return reviews.review(user.getId(), campaignId, submissionId, request);
    }

    @GetMapping({"/brand/campaigns/{campaignId}/proof/reviews", "/creator/campaigns/{campaignId}/proof/reviews",
            "/admin/campaigns/{campaignId}/proof/reviews"})
    CampaignProofReviewsResponse history(@AuthenticationPrincipal CurrentUser user, @PathVariable long campaignId) {
        return reviews.history(user.getId(), user.getRole(), campaignId);
    }
}
