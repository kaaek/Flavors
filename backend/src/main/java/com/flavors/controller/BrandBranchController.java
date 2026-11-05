package com.flavors.controller;

import com.flavors.dto.BranchRegistrationRequest;
import com.flavors.dto.BranchResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.service.BrandBranchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brand/branches")
public class BrandBranchController {

    private final BrandBranchService branches;

    public BrandBranchController(BrandBranchService branches) {
        this.branches = branches;
    }

    @GetMapping
    List<BranchResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return branches.list(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BranchResponse add(@AuthenticationPrincipal CurrentUser user,
                       @Valid @RequestBody BranchRegistrationRequest request) {
        return branches.add(user.getId(), request);
    }
}
