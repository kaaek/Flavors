package com.flavors.platform.outbox;

import com.flavors.platform.web.CorrelationContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an event into the {@code outbox} table. It joins the caller's transaction (MANDATORY, like the
 * audit logger), so the event exists exactly when the business change committed: no lost event, no event
 * for a rolled-back change. {@link OutboxRelay} publishes it afterwards.
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public OutboxWriter(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param type          event type and routing key, e.g. {@code campaign.proof-approved}
     * @param version       payload schema version, starting at 1
     * @param aggregateType the kind of thing that changed
     * @param aggregateId   which one (events of the same aggregate keep their order)
     * @param stateVersion  the aggregate's version after the change; null if it is not versioned
     * @param payload       a record from {@code libs/contracts}; must be JSON-serializable
     * @return the message id (the consumers' dedupe key)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID write(String type, int version, String aggregateType, String aggregateId, Long stateVersion,
                      Object payload) {
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox (message_id, type, version, correlation_id, aggregate_type, aggregate_id,
                                    state_version, occurred_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, messageId, type, version, CorrelationContext.current(), aggregateType, aggregateId,
                stateVersion, java.sql.Timestamp.from(clock.instant()), json.writeValueAsString(payload));
        return messageId;
    }
}
