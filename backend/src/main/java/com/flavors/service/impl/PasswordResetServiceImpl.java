package com.flavors.service.impl;

import com.flavors.entity.AppUser;
import com.flavors.entity.PasswordResetToken;
import com.flavors.exception.InvalidPasswordResetTokenException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.PasswordResetTokenRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.PasswordResetMailSender;
import com.flavors.service.PasswordResetService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender mailSender;
    private final AuditLogger auditLogger;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Autowired
    public PasswordResetServiceImpl(AppUserRepository users, PasswordResetTokenRepository tokens,
                                    PasswordEncoder passwordEncoder, PasswordResetMailSender mailSender,
                                    AuditLogger auditLogger) {
        this(users, tokens, passwordEncoder, mailSender, auditLogger, Clock.systemUTC());
    }

    PasswordResetServiceImpl(AppUserRepository users, PasswordResetTokenRepository tokens,
                             PasswordEncoder passwordEncoder, PasswordResetMailSender mailSender,
                             AuditLogger auditLogger, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void requestReset(String email) {
        users.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            tokens.save(new PasswordResetToken(user.getId(), sha256(rawToken),
                    clock.instant().plus(TOKEN_LIFETIME)));
            mailSender.sendResetLink(user.getEmail(), rawToken);
            auditLogger.record("PASSWORD_RESET_REQUESTED", "app_user", user.getId());
        });
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHashForUpdate(sha256(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (!token.canBeUsedAt(now)) {
            throw new InvalidPasswordResetTokenException();
        }

        AppUser user = users.findById(token.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        auditLogger.record("PASSWORD_RESET", "app_user", user.getId());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
