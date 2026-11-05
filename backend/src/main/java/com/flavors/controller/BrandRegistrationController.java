package com.flavors.controller;

import com.flavors.dto.BrandRegistrationRequest;
import com.flavors.dto.BrandRegistrationResponse;
import com.flavors.service.BrandRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrandRegistrationController {

    private final BrandRegistrationService registration;

    public BrandRegistrationController(BrandRegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/api/public/auth/register/brand")
    @ResponseStatus(HttpStatus.CREATED)
    BrandRegistrationResponse register(@Valid @RequestBody BrandRegistrationRequest request) {
        return registration.register(request);
    }
}
