package com.flavors.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The wrapper around every event that crosses a service boundary (EEC-95 §1.3). A service writes one
 * into its outbox in the same transaction as the change it announces; consumers dedupe on
 * {@link #messageId()} through their inbox table.
 *
 * @param messageId     unique per message; the inbox primary key, so a redelivery is a no-op
 * @param type          what happened, e.g. {@code template.ping}; also the RabbitMQ routing key
 * @param version       schema version of {@code payload}; a breaking change adds a new version, it
 *                      never edits an old one
 * @param correlationId ties one user action across services' logs and audit rows (the gateway request id)
 * @param occurredAt    when the producing transaction made the change (UTC)
 * @param aggregateType the kind of thing that changed, e.g. {@code campaign}
 * @param aggregateId   which one; also the ordering key, so events of one aggregate stay in order
 * @param stateVersion  the aggregate's version after the change; lets a consumer drop a stale command
 *                      that arrives late (R2, R4 in the plan). Null when the aggregate is not versioned
 * @param payload       the versioned event body, see {@code com.flavors.contracts.events}
 */
public record EventEnvelope<T>(
        UUID messageId,
        String type,
        int version,
        String correlationId,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        Long stateVersion,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (version < 1) {
            throw new IllegalArgumentException("version starts at 1, was " + version);
        }
    }
}
