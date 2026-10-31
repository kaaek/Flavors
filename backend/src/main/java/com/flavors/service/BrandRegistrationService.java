package com.flavors.service;

import com.flavors.dto.BrandRegistrationRequest;
import com.flavors.dto.BrandRegistrationResponse;

public interface BrandRegistrationService {

    BrandRegistrationResponse register(BrandRegistrationRequest request);
}
