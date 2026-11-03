package com.flavors.service;

import com.flavors.dto.CreatorRegistrationRequest;
import com.flavors.dto.CreatorRegistrationResponse;

public interface CreatorRegistrationService {

    /** UC-F19.1: creates the creator as PENDING_APPROVAL, so she lands in the admin queue (FR-F19.4). */
    CreatorRegistrationResponse register(CreatorRegistrationRequest request);
}
