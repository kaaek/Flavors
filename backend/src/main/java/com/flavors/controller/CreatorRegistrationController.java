package com.flavors.controller;

import com.flavors.dto.CreatorRegistrationRequest;
import com.flavors.dto.CreatorRegistrationResponse;
import com.flavors.service.CreatorRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreatorRegistrationController {

    private final CreatorRegistrationService registration;

    public CreatorRegistrationController(CreatorRegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/api/public/auth/register/creator")
    @ResponseStatus(HttpStatus.CREATED)
    CreatorRegistrationResponse register(@Valid @RequestBody CreatorRegistrationRequest request) {
        return registration.register(request);
    }
}
