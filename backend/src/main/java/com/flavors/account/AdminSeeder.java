package com.flavors.account;

import com.flavors.common.audit.AuditLogger;
import com.flavors.common.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first ADMIN account (acceptance criterion 4), since nobody can register as one.
 * Off unless {@code FLAVORS_SEED_ADMIN_ENABLED=true}. Safe to leave on: it does nothing once the
 * email exists. The password comes from the environment, never from git or a migration.
 * See docs/Architecture.md §8.
 */
@Component
@ConditionalOnProperty(name = "flavors.seed.admin.enabled", havingValue = "true")
public class AdminSeeder implements ApplicationRunner {

    private final String email;
    private final String password;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;
    private final TransactionTemplate tx;

    public AdminSeeder(@Value("${flavors.admin.email:}") String email,
                       @Value("${flavors.admin.password:}") String password,
                       AppUserRepository users, PasswordEncoder passwordEncoder,
                       AuditLogger auditLogger, TransactionTemplate tx) {
        this.email = email;
        this.password = password;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Fail at startup rather than run without an admin, or with an empty password.
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Admin seeding is enabled but FLAVORS_ADMIN_EMAIL or FLAVORS_ADMIN_PASSWORD is not set");
        }
        try {
            // One transaction, so the account and its audit entry exist together or not at all.
            tx.executeWithoutResult(status -> {
                if (users.findByEmailIgnoreCase(email).isPresent()) {
                    return;
                }
                AppUser admin = users.save(new AppUser(email, passwordEncoder.encode(password), Role.ADMIN));
                // No one is logged in at startup, so AuditLogger records the actor as 'system'.
                auditLogger.record("ADMIN_SEEDED", "app_user", admin.getId());
            });
        } catch (DataIntegrityViolationException e) {
            // Another instance starting at the same moment created it first (unique email index): fine.
        }
    }
}
