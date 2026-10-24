package com.flavors.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Acceptance criterion 2 (NF-7): audit rows can't be changed or removed. These tests go around
 * the application on purpose and use plain SQL, to show that the database triggers in
 * V1__foundation.sql block the change whatever code sends it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditLogImmutabilityIT {

    @Autowired
    JdbcTemplate jdbc;

    long id;

    @BeforeEach
    void insertRow() {
        id = jdbc.queryForObject("""
                INSERT INTO audit_log (actor_label, action, entity_type, entity_id)
                VALUES ('system', 'TEST_ACTION', 'test', 1)
                RETURNING id
                """, Long.class);
    }

    @Test
    void updateIsRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'CHANGED' WHERE id = ?", id))
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT action FROM audit_log WHERE id = ?", String.class, id))
                .isEqualTo("TEST_ACTION");
    }

    @Test
    void deleteIsRejected() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", id))
                .hasMessageContaining("append-only");
        assertThat(rowExists()).isTrue();
    }

    @Test
    void truncateIsRejected() {
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log"))
                .hasMessageContaining("append-only");
        assertThat(rowExists()).isTrue();
    }

    private boolean rowExists() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE id = ?", Integer.class, id) == 1;
    }
}
