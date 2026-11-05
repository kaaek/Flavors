package com.flavors.service;

import com.flavors.entity.AuditLogEntry;
import java.util.Map;

/**
 * The only way features write to the audit log (NF-7). The actor comes from the security context,
 * or is {@code system} for jobs and the seeder, where nobody is logged in.
 *
 * <p>Must be called inside a transaction (it throws otherwise), so the audit row commits or rolls
 * back together with the business change it describes.
 */
public interface AuditLogger {

    /** @param details extra facts about the action, stored as jsonb; may be null */
    AuditLogEntry record(String action, String entityType, Long entityId, Map<String, Object> details);

    AuditLogEntry record(String action, String entityType, Long entityId);
}
