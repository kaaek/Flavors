package com.flavors.service;

import com.flavors.dto.CreatorPortfolioResponse;

/** A brand looks at a creator's verified portfolio (UC-F1.1). */
public interface PortfolioViewService {

    /**
     * The portfolio of an approved creator.
     *
     * @throws com.flavors.exception.CreatorNotFoundException if she does not exist or is not approved
     */
    CreatorPortfolioResponse view(long creatorId);
}