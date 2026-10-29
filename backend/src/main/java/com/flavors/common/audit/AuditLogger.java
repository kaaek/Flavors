package com.flavors.common.audit;

import com.flavors.common.security.CurrentUser;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way features write to the audit log (NF-7). The actor comes from the security context,
 * or is {@code system} for jobs and the seeder, where nobody is logged in.
 */
@Service
public class AuditLogger {

    static final String SYSTEM_LABEL = "system";

    private final AuditLogRepository repository;

    public AuditLogger(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * MANDATORY: the audit row must join the caller's transaction, so it commits or rolls back
     * together with the business change. Calling this without a transaction fails loudly instead of
     * silently auditing something that may later roll back (or the reverse).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLogEntry record(String action, String entityType, Long entityId, Map<String, Object> details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = null;
        String actorLabel = SYSTEM_LABEL;
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            actorId = user.getId();
            actorLabel = user.getUsername();
        }
        return repository.save(new AuditLogEntry(actorId, actorLabel, action, entityType, entityId, details));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLogEntry record(String action, String entityType, Long entityId) {
        return record(action, entityType, entityId, null);
    }
}
