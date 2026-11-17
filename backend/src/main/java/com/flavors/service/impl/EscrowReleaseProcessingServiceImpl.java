package com.flavors.service.impl;

import com.flavors.service.EscrowReleaseProcessingService;
import com.flavors.service.EscrowReleaseService;
import com.flavors.service.ProofReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscrowReleaseProcessingServiceImpl implements EscrowReleaseProcessingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EscrowReleaseProcessingServiceImpl.class);
    private final ProofReviewService reviews;
    private final EscrowReleaseService releases;

    public EscrowReleaseProcessingServiceImpl(ProofReviewService reviews, EscrowReleaseService releases) {
        this.reviews = reviews;
        this.releases = releases;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int authorizeOverdueBatch() {
        int authorized = 0;
        for (long campaignId : reviews.findOverdueCampaignIds()) {
            try {
                // Separate proxied service: commit/rollback (including audit) completes before continuing.
                if (releases.authorizeOverdue(campaignId)) {
                    authorized++;
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Could not authorize Timer C for campaign {} ({}); will retry on a later run.",
                        campaignId, e.getClass().getSimpleName());
            }
        }
        return authorized;
    }
}
