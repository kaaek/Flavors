package com.flavors.platform.outbox;

import com.flavors.contracts.EventEnvelope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes unpublished {@code outbox} rows to RabbitMQ, oldest first, and marks each one published only
 * after the broker confirmed it. Delivery is therefore at-least-once; consumers dedupe through the inbox.
 *
 * <p>One relay runs at a time per database: a transaction-scoped Postgres advisory lock lets replicas of
 * a service take turns without publishing out of order. A failure stops the batch (so order is kept),
 * counts the attempt, and the next run retries from the same row.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Arbitrary, but fixed: the same number on every replica of every service database. */
    private static final long LOCK_KEY = 0x464C41564F5253L; // "FLAVORS"

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RabbitTemplate rabbit;
    private final EventCodec codec;
    private final JsonMapper json;
    private final OutboxProperties properties;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, RabbitTemplate rabbit,
                       EventCodec codec, JsonMapper json, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.rabbit = rabbit;
        this.codec = codec;
        this.json = json;
        this.properties = properties;
        if (!rabbit.getConnectionFactory().isPublisherConfirms()) {
            throw new IllegalStateException(
                    "The outbox relay needs publisher confirms: set spring.rabbitmq.publisher-confirm-type=correlated");
        }
    }

    private record Row(long seq, UUID messageId, String type, int version, String correlationId,
                       String aggregateType, String aggregateId, Long stateVersion,
                       java.time.Instant occurredAt, String payload) {
    }

    /** One relay run; returns how many events were published. Called by the scheduler, and by tests. */
    public int publishPending() {
        Integer published = transactions.execute(status -> {
            Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
            if (!Boolean.TRUE.equals(locked)) {
                return 0; // another replica is relaying right now
            }
            List<Row> rows = jdbc.query("""
                    SELECT seq, message_id, type, version, correlation_id, aggregate_type, aggregate_id,
                           state_version, occurred_at, payload::text
                    FROM outbox WHERE published_at IS NULL ORDER BY seq LIMIT ?
                    """, (rs, i) -> new Row(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                    rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getObject(8, Long.class), rs.getObject(9, OffsetDateTime.class).toInstant(),
                    rs.getString(10)), properties.batchSize());
            int count = 0;
            for (Row row : rows) {
                try {
                    publish(row);
                } catch (RuntimeException e) {
                    log.warn("Outbox message {} ({}) was not published, will retry: {}", row.messageId(),
                            row.type(), e.toString());
                    jdbc.update("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE seq = ?",
                            truncate(e.toString()), row.seq());
                    break; // keep the order: nothing after a failed row goes out before it
                }
                jdbc.update("UPDATE outbox SET published_at = now(), attempts = attempts + 1, last_error = NULL "
                        + "WHERE seq = ?", row.seq());
                count++;
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    private void publish(Row row) {
        JsonNode payload = json.readTree(row.payload());
        byte[] body = codec.encode(new EventEnvelope<>(row.messageId(), row.type(), row.version(),
                row.correlationId(), row.occurredAt(), row.aggregateType(), row.aggregateId(),
                row.stateVersion(), payload));
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(row.messageId().toString());
        props.setType(row.type());
        props.setCorrelationId(row.correlationId());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader("version", row.version());
        props.setHeader("aggregateType", row.aggregateType());
        props.setHeader("aggregateId", row.aggregateId());
        Message message = MessageBuilder.withBody(body).andProperties(props).build();
        rabbit.invoke(operations -> {
            operations.send(properties.exchange(), row.type(), message);
            operations.waitForConfirmsOrDie(properties.confirmTimeout().toMillis());
            return null;
        });
    }

    private static String truncate(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
