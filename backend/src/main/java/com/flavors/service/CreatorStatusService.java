package com.flavors.service;

import com.flavors.dto.CreatorStatusResponse;

public interface CreatorStatusService {

    /** The logged-in creator's own approval status (FR-F20.5). */
    CreatorStatusResponse statusOf(long creatorId);
}
