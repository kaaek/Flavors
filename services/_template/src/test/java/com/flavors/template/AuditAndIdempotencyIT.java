package com.flavors.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.platform.audit.AuditLogger;
import com.flavors.platform.idempotency.IdempotencyConflictException;
import com.flavors.platform.idempotency.IdempotencyService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AuditAndIdempotencyIT extends IntegrationTestBase {

    @Autowired
    AuditLogger audit;
    @Autowired
    IdempotencyService idempotency;
    @Autowired
    TransactionTemplate tx;

    private int auditRows(String action, String note) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ? AND details->>'note' = ?",
                Integer.class, action, note);
    }

    @Test
    void anEndpointCallWritesAnAuditRowWithTheTokensUserAsActor() {
        Response response = postJson("/api/template/ping",
                Map.of("Authorization", "Bearer " + token(77, "BRAND", null)), "{\"note\":\"audited\"}");

        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT actor_user_id, actor_label, entity_type FROM audit_log WHERE details->>'note' = 'audited'");
        assertThat(row.get("actor_user_id")).isEqualTo(77L);
        assertThat(row.get("actor_label")).isEqualTo("user:77");
        assertThat(row.get("entity_type")).isEqualTo("template");
    }

    @Test
    void auditJoinsTheCallersTransactionSoARollbackTakesTheRowWithIt() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            audit.record("ROLLED_BACK", "template", 1L, Map.of("note", "rollback-check"));
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(auditRows("ROLLED_BACK", "rollback-check")).isZero();
    }

    @Test
    void auditOutsideATransactionFailsLoudly() {
        assertThatThrownBy(() -> audit.record("NO_TX", "template", 1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void auditRowsWrittenByTheSystemUseTheSystemLabel() {
        tx.executeWithoutResult(status -> audit.record("SYSTEM_JOB", "template", 5L, Map.of("note", "job")));

        assertThat(jdbc.queryForObject(
                "SELECT actor_label FROM audit_log WHERE action = 'SYSTEM_JOB' AND details->>'note' = 'job'",
                String.class)).isEqualTo("system");
    }

    @Test
    void theDatabaseRefusesToChangeOrRemoveAuditRows() {
        tx.executeWithoutResult(status -> audit.record("IMMUTABLE", "template", 1L));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'X' WHERE action = 'IMMUTABLE'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE action = 'IMMUTABLE'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void aRetryWithTheSameKeyRunsTheActionOnce() {
        AtomicInteger runs = new AtomicInteger();

        String first = idempotency.execute("scope", "k1", "fingerprint", () -> "run-" + runs.incrementAndGet(),
                String.class);
        String retry = idempotency.execute("scope", "k1", "fingerprint", () -> "run-" + runs.incrementAndGet(),
                String.class);

        assertThat(first).isEqualTo("run-1");
        assertThat(retry).isEqualTo("run-1");
        assertThat(runs).hasValue(1);
    }

    @Test
    void theSameKeyForADifferentRequestIsAConflict() {
        idempotency.execute("scope", "k2", "fingerprint-a", () -> "a", String.class);

        assertThatThrownBy(() -> idempotency.execute("scope", "k2", "fingerprint-b", () -> "b", String.class))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aFailedActionLeavesNoKeyBehindSoALaterRetryRuns() {
        assertThatThrownBy(() -> idempotency.execute("scope", "k3", "f", () -> {
            throw new IllegalStateException("boom");
        }, String.class)).isInstanceOf(IllegalStateException.class);

        assertThat(idempotency.execute("scope", "k3", "f", () -> "second try", String.class))
                .isEqualTo("second try");
    }

    @Test
    void anIdempotentHttpRetryWritesOneAuditRowAndOneEvent() {
        Map<String, String> headers = Map.of("Authorization", "Bearer " + token(3, "CREATOR", null),
                "Idempotency-Key", "http-retry-1");
        int eventsBefore = jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class);

        Response first = postJson("/api/template/ping", headers, "{\"note\":\"retried\"}");
        Response retry = postJson("/api/template/ping", headers, "{\"note\":\"retried\"}");

        assertThat(first.status()).isEqualTo(200);
        assertThat(retry.body()).isEqualTo(first.body());
        assertThat(auditRows("TEMPLATE_PING", "retried")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(eventsBefore + 1);
    }

    @Test
    void anIdempotencyKeyIsNotSharedBetweenUsers() {
        Response a = postJson("/api/template/ping", Map.of("Authorization", "Bearer " + token(1, "BRAND", null),
                "Idempotency-Key", "shared-key"), "{\"note\":\"user-a\"}");
        Response b = postJson("/api/template/ping", Map.of("Authorization", "Bearer " + token(2, "BRAND", null),
                "Idempotency-Key", "shared-key"), "{\"note\":\"user-b\"}");

        assertThat(b.status()).isEqualTo(200); // not a conflict, and not user A's stored result
        assertThat(b.body()).isNotEqualTo(a.body());
        assertThat(auditRows("TEMPLATE_PING", "user-b")).isEqualTo(1);
    }

    @Test
    void validationErrorsAreProblemDetails() {
        Response response = postJson("/api/template/ping", Map.of("Authorization", "Bearer " + token(3, "CREATOR", null)),
                "{\"note\":\"\"}");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("\"errors\"").contains("note");
    }
}
