package com.flavors.platform.audit;

import com.flavors.platform.security.CurrentUser;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes audit rows with plain SQL; the contract is documented on {@link AuditLogger}. */
public class JdbcAuditLogger implements AuditLogger {

    static final String SYSTEM_LABEL = "system";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public JdbcAuditLogger(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * MANDATORY: the audit row must join the caller's transaction, so it commits or rolls back together
     * with the business change. Calling this without a transaction fails loudly instead of silently
     * auditing something that may later roll back (or the reverse).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(String action, String entityType, Long entityId, Map<String, Object> details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = null;
        String actorLabel = SYSTEM_LABEL;
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            actorId = user.id();
            actorLabel = user.getUsername();
        }
        String detailsJson = details == null ? null : json.writeValueAsString(details);
        Long finalActorId = actorId;
        String finalActorLabel = actorLabel;
        // occurred_at is left to the column default (now()), so no application clock can change it.
        return jdbc.queryForObject("""
                INSERT INTO audit_log (actor_user_id, actor_label, action, entity_type, entity_id, details)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                RETURNING id, occurred_at
                """,
                (rs, i) -> new AuditEntry(rs.getLong(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                        finalActorId, finalActorLabel, action, entityType, entityId, details),
                actorId, actorLabel, action, entityType, entityId, detailsJson);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(String action, String entityType, Long entityId) {
        return record(action, entityType, entityId, null);
    }
}
