package com.flavors.controller;

import com.flavors.dto.CreatorReviewResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.PendingCreatorResponse;
import com.flavors.dto.RejectCreatorRequest;
import com.flavors.service.CreatorReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-F20.1. SecurityConfig lets only ADMIN reach /api/admin/**. */
@RestController
@RequestMapping("/api/admin/creators")
public class AdminCreatorReviewController {

    private final CreatorReviewService reviews;

    public AdminCreatorReviewController(CreatorReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/pending")
    List<PendingCreatorResponse> pending() {
        return reviews.listPending();
    }

    @GetMapping("/{id}")
    CreatorReviewResponse get(@PathVariable long id) {
        return reviews.get(id);
    }

    @PostMapping("/{id}/approve")
    CreatorReviewResponse approve(@AuthenticationPrincipal CurrentUser admin, @PathVariable long id) {
        return reviews.approve(admin.getId(), id);
    }

    @PostMapping("/{id}/reject")
    CreatorReviewResponse reject(@AuthenticationPrincipal CurrentUser admin, @PathVariable long id,
                                 @Valid @RequestBody RejectCreatorRequest request) {
        return reviews.reject(admin.getId(), id, request.reason());
    }
}
