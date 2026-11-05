package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.MeResponse;
import com.flavors.mapper.MeMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the SPA who is logged in, so it can show the right area (creator portal, brand dashboard,
 * admin console). Not logged in gives 401 (SecurityConfig), which the SPA turns into the login page.
 */
@RestController
class MeController {

    private final MeMapper mapper;

    MeController(MeMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return mapper.toResponse(user);
    }
}
