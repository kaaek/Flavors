package com.flavors.common.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints, one in each URL area of SecurityConfig, so RoleGuardTest can check the
 * rules before any real feature endpoint exists.
 */
@RestController
class RoleProbeController {

    @GetMapping({"/api/public/probe", "/api/creator/probe", "/api/brand/probe", "/api/admin/probe", "/api/any/probe"})
    String get() {
        return "ok";
    }

    @PostMapping("/api/creator/probe")
    String post() {
        return "ok";
    }
}
