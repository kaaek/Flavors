package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.flavors.service.PasswordResetMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

class PasswordResetMailProviderSelectionTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withUserConfiguration(SmtpPasswordResetMailSender.class, ResendPasswordResetMailSender.class)
            .withPropertyValues(
                    "flavors.mail.from=Flavors <reset@mail.flavors.example>",
                    "flavors.app.base-url=https://flavors-staging.example",
                    "RESEND_API_KEY=test-only-key");

    @Test
    void smtpIsDefault() {
        contexts.run(context -> {
            assertThat(context).hasSingleBean(PasswordResetMailSender.class);
            assertThat(context).hasSingleBean(SmtpPasswordResetMailSender.class);
            assertThat(context).doesNotHaveBean(ResendPasswordResetMailSender.class);
        });
    }

    @Test
    void resendIsSelectedWhenConfigured() {
        contexts.withPropertyValues("flavors.mail.provider=resend").run(context -> {
            assertThat(context).hasSingleBean(PasswordResetMailSender.class);
            assertThat(context).hasSingleBean(ResendPasswordResetMailSender.class);
            assertThat(context).doesNotHaveBean(SmtpPasswordResetMailSender.class);
        });
    }
}
