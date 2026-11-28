package com.flavors.platform.audit;

import java.util.Map;

/**
 * The only way features write to the audit log (NF-7). The actor comes from the security context, or is
 * {@code system} for jobs and seeders, where nobody is logged in.
 *
 * <p>Must be called inside a transaction (it throws otherwise), so the audit row commits or rolls back
 * together with the business change it describes. Every service has its own {@code audit_log} table; see
 * {@code db/templates/audit_log.sql} in this library.
 */
public interface AuditLogger {

    /** @param details extra facts about the action, stored as jsonb; may be null */
    AuditEntry record(String action, String entityType, Long entityId, Map<String, Object> details);

    AuditEntry record(String action, String entityType, Long entityId);
}
