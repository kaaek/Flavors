package com.flavors.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the constraints in V1__foundation.sql that later code relies on. Each test uses its own
 * random email or key, so the tests don't depend on each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FoundationSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void emailIsUniqueIgnoringCase() {
        String email = unique() + "@example.com";
        insertUser(email, "CREATOR", "ACTIVE");

        assertThatThrownBy(() -> insertUser(email.toUpperCase(), "CREATOR", "ACTIVE"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void unknownRoleIsRejected() {
        assertThatThrownBy(() -> insertUser(unique() + "@example.com", "DINER", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> insertUser(unique() + "@example.com", "BRAND", "DELETED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameIdempotencyKeyIsInsertedOnlyOnce() {
        String key = unique();
        String insert = """
                INSERT INTO idempotency_key (scope, key, request_hash, status)
                VALUES ('test', ?, 'hash', 'IN_PROGRESS')
                ON CONFLICT DO NOTHING
                """;

        assertThat(jdbc.update(insert, key)).isEqualTo(1);
        assertThat(jdbc.update(insert, key)).isZero();
    }

    @Test
    void completedIdempotencyKeyNeedsCompletedAt() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO idempotency_key (scope, key, request_hash, status)
                VALUES ('test', ?, 'hash', 'COMPLETED')
                """, unique()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(String email, String role, String status) {
        jdbc.update("INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'x', ?, ?)",
                email, role, status);
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }
}
