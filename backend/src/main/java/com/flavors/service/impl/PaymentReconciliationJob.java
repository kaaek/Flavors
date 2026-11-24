package com.flavors.service.impl;

import com.flavors.service.PaymentService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for payments whose callback never arrived (or arrived while Flavors was down): every few minutes it asks the
 * provider about payments still PENDING on campaigns waiting for payment. Payments younger than two minutes are left
 * alone, so the normal callback gets the first word.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private static final Duration GRACE = Duration.ofMinutes(2);

    private final PaymentService paymentService;

    public PaymentReconciliationJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(cron = "${flavors.payment.reconciliation-cron:0 */5 * * * *}")
    public void reconcile() {
        int checked = paymentService.reconcileOpenPayments(GRACE);
        if (checked > 0) {
            LOGGER.info("Payment reconciliation checked {} open payments.", checked);
        }
    }
}
