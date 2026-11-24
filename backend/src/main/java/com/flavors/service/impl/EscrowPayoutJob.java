package com.flavors.service.impl;

import com.flavors.service.EscrowPayoutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EscrowPayoutJob {
    private final EscrowPayoutService payouts;
    public EscrowPayoutJob(EscrowPayoutService payouts) { this.payouts = payouts; }

    @Scheduled(cron = "${flavors.payout.processing-cron:0 * * * * *}",
            zone = "${flavors.payout.processing-zone:Asia/Beirut}")
    public void processPayouts() { payouts.processPendingPayouts(); }
}
