package com.flavors.common.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Deliberately extends the bare {@link Repository}, not JpaRepository: only the methods below exist,
 * so no code can call delete or deleteAll on the audit log.
 */
public interface AuditLogRepository extends Repository<AuditLogEntry, Long> {

    AuditLogEntry save(AuditLogEntry entry);

    Optional<AuditLogEntry> findById(Long id);

    /** History of one record, oldest first (e.g. everything that happened to campaign 7). */
    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);

    List<AuditLogEntry> findByActorUserIdOrderByIdAsc(Long actorUserId);
}
