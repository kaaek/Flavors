package com.flavors.controller;

import com.flavors.dto.CampaignReviewRequest;
import com.flavors.dto.CurrentUser;
import com.flavors.entity.Review;
import com.flavors.service.CampaignReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.flavors.exception.ValidationFailedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/brand/campaigns")
public class CampaignReviewController {

    private final CampaignReviewService reviewService;

    public CampaignReviewController(CampaignReviewService reviewService) {
        this.reviewService = reviewService;
    }
    @GetMapping("/{id}/review")
    public ResponseEntity<Review> get(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long id) {

        Review review = reviewService.get(user.getId(), id);

        return review == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(review);
    }

    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public Review submit(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long id,
            @RequestBody CampaignReviewRequest request) {

        if (request == null || request.rating() == null) {
            throw new ValidationFailedException("rating", "Rating is required");
        }

        return reviewService.submit(
                user.getId(),
                id,
                request.rating(),
                request.text());
    }
}
