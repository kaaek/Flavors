package com.flavors.platform.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the append-only audit log. {@code occurredAt} comes from the database clock.
 * ({@code id} and {@code occurredAt} are what the insert returns; the rest echoes the arguments.)
 */
public record AuditEntry(Long id, Instant occurredAt, Long actorUserId, String actorLabel, String action,
                         String entityType, Long entityId, Map<String, Object> details) {
}
