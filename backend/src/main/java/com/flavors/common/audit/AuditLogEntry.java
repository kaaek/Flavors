package com.flavors.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * One row of the append-only audit log (table {@code audit_log}, V1; NF-7). {@code @Immutable} makes
 * Hibernate ignore any change to a loaded entry, and there are no setters. The database triggers
 * are the real guarantee; this just keeps the app from trying.
 */
@Entity
@Table(name = "audit_log")
@Immutable
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Set by the database (now()), so no application server's clock can change it; Hibernate reads
    // it back after the insert.
    @Generated(event = EventType.INSERT)
    @Column(nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    // Null for actions done by the system (scheduled jobs, the admin seeder).
    private Long actorUserId;

    @Column(nullable = false)
    private String actorLabel;

    @Column(nullable = false)
    private String action;

    private String entityType;

    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    /** For JPA only. */
    protected AuditLogEntry() {
    }

    public AuditLogEntry(Long actorUserId, String actorLabel, String action,
                         String entityType, Long entityId, Map<String, Object> details) {
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
