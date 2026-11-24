package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.flavors.service.PasswordResetMailEvent;
import com.flavors.service.PasswordResetMailSender;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

class PasswordResetMailListenerTest {

    @Test
    void deliveryRemainsPendingUntilExecutorRunsIt() {
        PasswordResetMailSender sender = mock(PasswordResetMailSender.class);
        Queue<Runnable> pending = new ArrayDeque<>();
        Executor executor = pending::add;
        var listener = new PasswordResetMailListener(sender, executor);

        listener.send(new PasswordResetMailEvent(42L, "creator@example.test", "secret-token"));

        assertThat(pending).hasSize(1);
        verifyNoInteractions(sender);
        pending.remove().run();
        verify(sender).sendResetLink("creator@example.test", "secret-token");
    }

    @Test
    void rejectedQueueDoesNotEscapeIntoTheRequest() {
        PasswordResetMailSender sender = mock(PasswordResetMailSender.class);
        Executor full = ignored -> { throw new RejectedExecutionException("queue full"); };
        new PasswordResetMailListener(sender, full)
                .send(new PasswordResetMailEvent(42L, "creator@example.test", "secret-token"));
        verifyNoInteractions(sender);
    }

    @Test
    void providerErrorIsContainedInWorker() {
        PasswordResetMailSender sender = mock(PasswordResetMailSender.class);
        doThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
                .when(sender).sendResetLink("creator@example.test", "secret-token");
        new PasswordResetMailListener(sender, Runnable::run)
                .send(new PasswordResetMailEvent(42L, "creator@example.test", "secret-token"));
        verify(sender).sendResetLink("creator@example.test", "secret-token");
    }
}
