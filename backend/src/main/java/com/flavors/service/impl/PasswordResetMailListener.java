package com.flavors.service.impl;

import com.flavors.service.PasswordResetMailEvent;
import com.flavors.service.PasswordResetMailSender;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientResponseException;

/** Queues delivery only after commit; never waits for Resend on the HTTP request thread. */
@Component
public class PasswordResetMailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetMailListener.class);
    private final PasswordResetMailSender mailSender;
    private final Executor mailExecutor;

    public PasswordResetMailListener(PasswordResetMailSender mailSender,
                                     @Qualifier("passwordResetMailExecutor") Executor mailExecutor) {
        this.mailSender = mailSender;
        this.mailExecutor = mailExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PasswordResetMailEvent event) {
        try {
            mailExecutor.execute(() -> deliver(event));
        } catch (RejectedExecutionException exception) {
            // Never leak registration status or fail the already-committed reset request.
            LOGGER.error("Password-reset email queue full for user {}: delivery not scheduled", event.userId());
        }
    }

    private void deliver(PasswordResetMailEvent event) {
        try {
            mailSender.sendResetLink(event.email(), event.rawToken());
        } catch (RestClientResponseException exception) {
            // Record the provider HTTP status, never its response body, recipient or token.
            LOGGER.error("Password-reset email delivery failed for user {}: provider HTTP {}",
                    event.userId(), exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            // Do not log the exception text or stack trace: some providers echo sensitive payloads.
            LOGGER.error("Password-reset email delivery failed for user {}: {}",
                    event.userId(), exception.getClass().getSimpleName());
        }
    }
}