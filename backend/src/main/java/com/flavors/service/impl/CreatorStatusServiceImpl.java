package com.flavors.service.impl;

import com.flavors.dto.CreatorStatusResponse;
import com.flavors.entity.CreatorProfile;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.CreatorStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorStatusServiceImpl implements CreatorStatusService {

    private final CreatorProfileRepository creators;

    public CreatorStatusServiceImpl(CreatorProfileRepository creators) {
        this.creators = creators;
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorStatusResponse statusOf(long creatorId) {
        CreatorProfile creator = creators.findById(creatorId)
                .orElseThrow(() -> new CreatorNotFoundException(creatorId));
        return new CreatorStatusResponse(creator.getVerificationStatus(), creator.getRejectionReason());
    }
}
