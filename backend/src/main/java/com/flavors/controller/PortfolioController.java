package com.flavors.controller;
 
import com.flavors.dto.CurrentUser;
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.service.PortfolioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
/**
 * The creator's Manage Portfolio page (UC-F1.2). Only creators reach {@code /api/creator/**}
 * (SecurityConfig), and the creator is always the logged-in user, so she can only touch her own portfolio.
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
    PortfolioResponse update(@AuthenticationPrincipal CurrentUser user, @RequestBody UpdatePortfolioRequest request) {
        return service.updateMyPortfolio(user.getId(), request);
    }
}