package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.flavors.TestcontainersConfiguration;
import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.exception.InvalidPasswordResetTokenException;
import com.flavors.repository.AppUserRepository;
import com.flavors.service.PasswordResetMailSender;
import com.flavors.service.PasswordResetService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PasswordResetServiceImplIT {

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

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
        verify(mailSender).sendResetLink(eq(email), token.capture());
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
    void unknownEmailRevealsNothingAndSendsNothing() {
        passwordResetService.requestReset(UUID.randomUUID() + "@example.com");

        verify(mailSender, never()).sendResetLink(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void expiredTokenIsRejected() {
        String email = UUID.randomUUID() + "@example.com";
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode("old-password"), Role.BRAND));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

        passwordResetService.requestReset(email);
        verify(mailSender).sendResetLink(eq(email), token.capture());
        jdbc.update("UPDATE password_reset_token SET expires_at = now() - interval '1 second' WHERE user_id = ?",
                user.getId());

        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getValue(), "new-password"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }
}
