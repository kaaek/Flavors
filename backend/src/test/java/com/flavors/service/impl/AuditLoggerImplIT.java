package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.entity.AuditLogEntry;
import com.flavors.enums.Role;
import com.flavors.repository.AuditLogRepository;
import com.flavors.service.AuditLogger;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The application side of the audit log. Audit rows can never be deleted, so each test uses a
 * random action name instead of cleaning up.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditLoggerImplIT {

    @Autowired
    AuditLogger auditLogger;

    @Autowired
    AuditLogRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsLoggedInUserAsActor() {
        String email = UUID.randomUUID() + "@example.com";
        long userId = jdbc.queryForObject(
                "INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'BRAND') RETURNING id",
                Long.class, email);
        CurrentUser user = new CurrentUser(userId, email, Role.BRAND, "x", true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        String action = "TEST_" + UUID.randomUUID();

        tx.executeWithoutResult(s -> auditLogger.record(action, "campaign", 7L, Map.of("from", "DRAFT", "to", "SENT")));

        AuditLogEntry entry = find(action);
        assertThat(entry.getActorUserId()).isEqualTo(userId);
        assertThat(entry.getActorLabel()).isEqualTo(email);
        assertThat(entry.getEntityType()).isEqualTo("campaign");
        assertThat(entry.getEntityId()).isEqualTo(7L);
        assertThat(entry.getDetails()).containsEntry("from", "DRAFT").containsEntry("to", "SENT");
        assertThat(entry.getOccurredAt()).isNotNull();
    }

    @Test
    void recordsSystemWhenNobodyIsLoggedIn() {
        String action = "TEST_" + UUID.randomUUID();

        tx.executeWithoutResult(s -> auditLogger.record(action, null, null));

        AuditLogEntry entry = find(action);
        assertThat(entry.getActorUserId()).isNull();
        assertThat(entry.getActorLabel()).isEqualTo("system");
        assertThat(entry.getDetails()).isNull();
    }

    @Test
    void auditRowRollsBackWithTheBusinessChange() {
        String action = "TEST_" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'CREATOR')", email);
            auditLogger.record(action, "app_user", null);
            throw new IllegalStateException("business step failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ?", Integer.class, action))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void refusesToRecordOutsideATransaction() {
        assertThatThrownBy(() -> auditLogger.record("TEST_NO_TX", null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void loadedEntryCannotBeChangedThroughJpa() throws Exception {
        String action = "TEST_" + UUID.randomUUID();
        tx.executeWithoutResult(s -> auditLogger.record(action, null, null));
        long id = find(action).getId();

        // Even forcing a field change and flushing sends no UPDATE (@Immutable); if one were sent,
        // the database trigger would reject it and this test would fail.
        tx.executeWithoutResult(s -> {
            AuditLogEntry entry = repository.findById(id).orElseThrow();
            try {
                Field field = AuditLogEntry.class.getDeclaredField("action");
                field.setAccessible(true);
                field.set(entry, "CHANGED");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            em.flush();
        });

        assertThat(find(action).getAction()).isEqualTo(action);
    }

    @Test
    void repositoryOffersNoDeleteOrUpdate() {
        assertThat(Arrays.stream(AuditLogRepository.class.getMethods()).map(m -> m.getName()))
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("remove"));
    }

    private AuditLogEntry find(String action) {
        Long id = jdbc.queryForObject("SELECT id FROM audit_log WHERE action = ?", Long.class, action);
        return tx.execute(s -> repository.findById(id).orElseThrow());
    }
}
