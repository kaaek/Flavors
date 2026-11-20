
package com.flavors.controller;

import com.flavors.dto.CampaignReviewRequest;
import com.flavors.dto.CampaignReviewResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.entity.Review;
import com.flavors.exception.ValidationFailedException;
import com.flavors.service.CampaignReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brand/campaigns")
public class CampaignReviewController {

    private final CampaignReviewService reviewService;

    public CampaignReviewController(CampaignReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{id}/review")
    public ResponseEntity<CampaignReviewResponse> get(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long id) {

        Review review = reviewService.get(user.getId(), id);

        return review == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(CampaignReviewResponse.from(review));
    }

    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignReviewResponse submit(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long id,
            @RequestBody CampaignReviewRequest request) {

        if (request == null
                || !(request.rating() instanceof Integer rating)
                || rating < 1
                || rating > 5) {
            throw new ValidationFailedException(
                    "rating", "Rating must be a whole number between 1 and 5");
        }

        Review review = reviewService.submit(
                user.getId(),
                id,
                rating.shortValue(),
                request.text());

        return CampaignReviewResponse.from(review);
    }
}
