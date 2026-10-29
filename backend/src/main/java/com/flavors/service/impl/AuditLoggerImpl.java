package com.flavors.service.impl;

import com.flavors.dto.CurrentUser;
import com.flavors.entity.AuditLogEntry;
import com.flavors.repository.AuditLogRepository;
import com.flavors.service.AuditLogger;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit rows through {@link AuditLogRepository}; the contract is documented on {@link AuditLogger}. */
@Service
public class AuditLoggerImpl implements AuditLogger {

    static final String SYSTEM_LABEL = "system";

    private final AuditLogRepository repository;

    public AuditLoggerImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * MANDATORY: the audit row must join the caller's transaction, so it commits or rolls back
     * together with the business change. Calling this without a transaction fails loudly instead of
     * silently auditing something that may later roll back (or the reverse).
     */
    @Override
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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLogEntry record(String action, String entityType, Long entityId) {
        return record(action, entityType, entityId, null);
    }
}
