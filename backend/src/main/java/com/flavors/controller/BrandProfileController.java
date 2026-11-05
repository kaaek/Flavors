package com.flavors.controller;

import com.flavors.dto.BrandProfileResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.UpdateBrandProfileRequest;
import com.flavors.service.BrandProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brand/profile")
public class BrandProfileController {

    private final BrandProfileService profile;

    public BrandProfileController(BrandProfileService profile) {
        this.profile = profile;
    }

    @GetMapping
    BrandProfileResponse get(@AuthenticationPrincipal CurrentUser user) {
        return profile.get(user.getId());
    }

    // The brand id comes from the session, never from the request.
    @PatchMapping
    BrandProfileResponse update(@AuthenticationPrincipal CurrentUser user,
                                @Valid @RequestBody UpdateBrandProfileRequest request) {
        return profile.update(user.getId(), request);
    }
}
