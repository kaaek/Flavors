package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.doAnswer;

import com.flavors.TestcontainersConfiguration;
import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.exception.InvalidPasswordResetTokenException;
import com.flavors.repository.AppUserRepository;
import com.flavors.service.PasswordResetMailSender;
import com.flavors.service.PasswordResetService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetServiceImplIT {

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JdbcIndexedSessionRepository sessions;

    @MockitoBean
    PasswordResetMailSender mailSender;

    @Test
    void emailedTokenChangesThePasswordOnce() {
        String email = UUID.randomUUID() + "@example.com";
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode("old-password"), Role.BRAND));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        Instant beforeRequest = Instant.now();

        passwordResetService.requestReset(email);
        Instant afterRequest = Instant.now();
        verify(mailSender, timeout(5000)).sendResetLink(eq(email), token.capture());
        Instant expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM password_reset_token WHERE user_id = ?",
                Instant.class, user.getId());
        assertThat(expiresAt).isBetween(beforeRequest.plus(Duration.ofHours(1)),
                afterRequest.plus(Duration.ofHours(1)));
        passwordResetService.resetPassword(token.getValue(), "new-password");

        AppUser changed = users.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("new-password", changed.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getValue(), "another-password"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetRequestReturnsWhileEmailDeliveryIsStillPending() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        users.save(new AppUser(email, passwordEncoder.encode("old-password"), Role.CREATOR));
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        doAnswer(invocation -> {
            deliveryStarted.countDown();
            releaseDelivery.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).sendResetLink(eq(email), org.mockito.ArgumentMatchers.anyString());
        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    mvc.perform(post("/api/public/auth/password-reset/request")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"email\":\"" + email + "\"}")
                                    .cookie(new Cookie("XSRF-TOKEN", "token"))
                                    .header("X-XSRF-TOKEN", "token"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.message").value(
                                    "If an account uses that email, a password-reset link has been sent.")));
            assertThat(deliveryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseDelivery.getCount()).isOne();
        } finally {
            releaseDelivery.countDown();
        }
    }

    @Test
    void unknownEmailRevealsNothingAndSendsNothing() {
        passwordResetService.requestReset(UUID.randomUUID() + "@example.com");

        verify(mailSender, never()).sendResetLink(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void expiredTokenIsRejected() {
        String email = UUID.randomUUID() + "@example.com";
    AppUser user = users.save(
            new AppUser(email, passwordEncoder.encode("old-password"), Role.BRAND)
    );
    ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

    passwordResetService.requestReset(email);
    verify(mailSender, timeout(5000))
            .sendResetLink(eq(email), token.capture());

    // Expire the token well before the current Java time.
    Instant expiredAt = Instant.now().minus(Duration.ofMinutes(5));

    int updated = jdbc.update(
            "UPDATE password_reset_token SET expires_at = ? WHERE user_id = ?",
            java.sql.Timestamp.from(expiredAt),
            user.getId()
    );

    // Confirm that the intended database row was updated.
    assertThat(updated).isEqualTo(1);

    Instant storedExpiry = jdbc.queryForObject(
            "SELECT expires_at FROM password_reset_token WHERE user_id = ?",
            (rs, rowNum) -> rs.getTimestamp(1).toInstant(),
            user.getId()
    );

    assertThat(storedExpiry).isBefore(Instant.now());

    // The expired token must not be accepted.
    assertThatThrownBy(() ->
            passwordResetService.resetPassword(token.getValue(), "new-password"))
            .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void newerRequestInvalidatesEveryEarlierUnusedToken() {
        String email = UUID.randomUUID() + "@example.com";
        users.save(new AppUser(email, passwordEncoder.encode("old-password"), Role.BRAND));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

        passwordResetService.requestReset(email);
        passwordResetService.requestReset(email);
        verify(mailSender, timeout(5000).times(2)).sendResetLink(eq(email), token.capture());

        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getAllValues().getFirst(),
                "first-new-password")).isInstanceOf(InvalidPasswordResetTokenException.class);
        passwordResetService.resetPassword(token.getAllValues().getLast(), "second-new-password");
    }

    @Test
    void smtpFailureDoesNotChangeTheGenericResetRequestOutcome() {
        String email = UUID.randomUUID() + "@example.com";
        users.save(new AppUser(email, passwordEncoder.encode("old-password"), Role.BRAND));
        doThrow(new IllegalStateException("SMTP is unavailable"))
                .when(mailSender).sendResetLink(eq(email), org.mockito.ArgumentMatchers.anyString());

        assertThatCode(() -> passwordResetService.requestReset(email)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_token WHERE user_id = "
                        + "(SELECT id FROM app_user WHERE email = ?)", Long.class, email))
                .isOne();
    }

    @Test
    void passwordResetInvalidatesExistingSessionsForTheUser() {
        String email = UUID.randomUUID() + "@example.com";
        users.save(new AppUser(email, passwordEncoder.encode("old-password"), Role.BRAND));
        Session session = sessions.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, email);
        saveSession(session);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

        passwordResetService.requestReset(email);
        verify(mailSender, timeout(5000)).sendResetLink(eq(email), token.capture());
        passwordResetService.resetPassword(token.getValue(), "new-password");

        assertThat(sessions.findById(session.getId())).isNull();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void saveSession(Session session) {
        // JdbcSession is package-private; use its public SessionRepository contract in the test.
        ((SessionRepository) sessions).save(session);
    }
}