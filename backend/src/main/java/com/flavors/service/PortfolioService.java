package com.flavors.service;
 
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
 
/** The creator's Manage Portfolio page (UC-F1.2). */
public interface PortfolioService {
 
    /** Her profile, connected accounts and completed campaigns. Needs an approved profile. */
    PortfolioResponse getMyPortfolio(long creatorId);
 
    /**
     * Saves her edited details and featured campaigns in one step. An invalid edit saves nothing
     * and throws {@link com.flavors.exception.InvalidPortfolioException}.
     */
    PortfolioResponse updateMyPortfolio(long creatorId, UpdatePortfolioRequest request);
}