package com.flavors.controller;

import com.flavors.dto.CreatorStatusResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.service.CreatorStatusService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreatorStatusController {

    private final CreatorStatusService statuses;

    public CreatorStatusController(CreatorStatusService statuses) {
        this.statuses = statuses;
    }

    @GetMapping("/api/creator/status")
    CreatorStatusResponse status(@AuthenticationPrincipal CurrentUser user) {
        return statuses.statusOf(user.getId());
    }
}
