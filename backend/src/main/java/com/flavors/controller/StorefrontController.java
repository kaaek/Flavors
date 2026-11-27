package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.StorefrontResponse;
import com.flavors.service.impl.StorefrontService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** EEC-48: anonymous storefront and the creator's own shareable URL metadata. */
@RestController
public class StorefrontController {
    private final StorefrontService service;

    public StorefrontController(StorefrontService service) {
        this.service = service;
    }

    @GetMapping("/api/public/storefronts/{slug}")
    public ResponseEntity<StorefrontResponse> publicStorefront(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publicStorefront(slug));
    }

    @GetMapping("/api/creator/storefront")
    public StorefrontResponse ownStorefront(@AuthenticationPrincipal CurrentUser user) {
        return service.ownStorefront(user.getId());
    }
}
