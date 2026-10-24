package com.flavors.account;

import com.flavors.common.security.CurrentUser;
import com.flavors.common.security.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the SPA who is logged in, so it can show the right area (creator portal, brand dashboard,
 * admin console). Not logged in gives 401 (SecurityConfig), which the SPA turns into the login page.
 */
@RestController
class MeController {

    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getRole());
    }

    record MeResponse(long id, String email, Role role) {
    }
}
