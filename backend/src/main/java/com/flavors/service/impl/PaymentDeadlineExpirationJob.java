package com.flavors.service.impl;

import com.flavors.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Timer B (FR-F7.6): expires accepted campaigns the brand did not pay for within 3 days. Same schedule style as Timer A. */
@Component
public class PaymentDeadlineExpirationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDeadlineExpirationJob.class);

    private final PaymentService paymentService;

    public PaymentDeadlineExpirationJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(
            cron = "${flavors.payment.deadline-expiration-cron:0 * * * * *}",
            zone = "${flavors.payment.deadline-expiration-zone:Asia/Beirut}")
    public void expireOverduePayments() {
        int expired = paymentService.expireOverduePayments();
        if (expired > 0) {
            LOGGER.info("Payment deadline expiration completed: {} campaigns expired.", expired);
        }
    }
}
