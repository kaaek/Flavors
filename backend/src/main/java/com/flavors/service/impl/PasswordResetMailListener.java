package com.flavors.service.impl;

import com.flavors.service.PasswordResetMailEvent;
import com.flavors.service.PasswordResetMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends reset mail only after the token transaction has committed. */
@Component
public class PasswordResetMailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetMailListener.class);

    private final PasswordResetMailSender mailSender;

    public PasswordResetMailListener(PasswordResetMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PasswordResetMailEvent event) {
        try {
            mailSender.sendResetLink(event.email(), event.rawToken());
        } catch (RuntimeException exception) {
            // Keep the public response indistinguishable from a request for an unknown email.
            LOGGER.warn("Could not send password-reset email for user {}", event.userId(), exception);
        }
    }
}
