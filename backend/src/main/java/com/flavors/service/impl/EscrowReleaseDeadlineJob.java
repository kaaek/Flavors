package com.flavors.service.impl;

import com.flavors.service.EscrowReleaseProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Timer C authorizes only; escrow remains HELD until a later payout implementation confirms payment. */
@Component
public class EscrowReleaseDeadlineJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(EscrowReleaseDeadlineJob.class);
    private final EscrowReleaseProcessingService processing;

    public EscrowReleaseDeadlineJob(EscrowReleaseProcessingService processing) {
        this.processing = processing;
    }

    @Scheduled(cron = "${flavors.escrow.release-deadline-cron:0 * * * * *}",
            zone = "${flavors.escrow.release-deadline-zone:Asia/Beirut}")
    public void authorizeOverdueReleases() {
        int authorized = processing.authorizeOverdueBatch();
        if (authorized > 0) {
            LOGGER.info("Timer C authorization completed: {} releases authorized.", authorized);
        }
    }
}
