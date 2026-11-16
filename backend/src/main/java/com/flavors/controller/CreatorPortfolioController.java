package com.flavors.controller;

import com.flavors.dto.CreatorPortfolioResponse;
import com.flavors.service.PortfolioViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A brand opens a creator's verified portfolio (UC-F1.1). Only brands reach {@code /api/brand/**}
 * (SecurityConfig). Any brand may look at any approved creator, so the creator comes from the address.
 */
@RestController
@RequestMapping("/api/brand/creators")
class CreatorPortfolioController {

    private final PortfolioViewService service;

    CreatorPortfolioController(PortfolioViewService service) {
        this.service = service;
    }

    @GetMapping("/{creatorId}/portfolio")
    CreatorPortfolioResponse portfolio(@PathVariable long creatorId) {
        return service.view(creatorId);
    }
}