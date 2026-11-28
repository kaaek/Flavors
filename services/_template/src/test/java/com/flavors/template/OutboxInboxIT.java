package com.flavors.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.flavors.contracts.EventEnvelope;
import com.flavors.contracts.events.v1.TemplatePingV1;
import com.flavors.platform.inbox.InboxService;
import com.flavors.platform.outbox.EventCodec;
import com.flavors.platform.outbox.OutboxWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxInboxIT extends IntegrationTestBase {

    @Autowired
    OutboxWriter outbox;
    @Autowired
    InboxService inbox;
    @Autowired
    EventCodec codec;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    RabbitTemplate rabbit;

    private int received(String note) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'TEMPLATE_PING_RECEIVED' AND details->>'note' = ?",
                Integer.class, note);
    }

    @Test
    void anEventTravelsFromTheOutboxThroughRabbitToTheConsumerExactlyOnce() {
        Response response = postJson("/api/template/ping",
                Map.of("Authorization", "Bearer " + token(11, "BRAND", null), "X-Correlation-Id", "corr-e2e"),
                "{\"note\":\"e2e\"}");
        assertThat(response.status()).isEqualTo(200);

        // The relay publishes it, the queue delivers it, the consumer audits it.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(received("e2e")).isEqualTo(1));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT published_at, correlation_id, type, version, aggregate_type FROM outbox "
                        + "WHERE payload->>'note' = 'e2e'");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("correlation_id")).isEqualTo("corr-e2e");
        assertThat(row.get("type")).isEqualTo("template.ping");
        assertThat(row.get("version")).isEqualTo(1);
    }

    @Test
    void aRedeliveredMessageIsHandledOnlyOnce() {
        UUID id = UUID.randomUUID();
        var envelope = new EventEnvelope<>(id, TemplatePingV1.TYPE, 1, "corr-dup", Instant.now(), "template", "ping",
                null, new TemplatePingV1("dup"));
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = MessageBuilder.withBody(codec.encode(envelope)).andProperties(props).build();

        rabbit.send("flavors.events", TemplatePingV1.TYPE, message);
        rabbit.send("flavors.events", TemplatePingV1.TYPE, message); // the broker delivers it twice
        // A different message afterwards proves the queue has drained past both copies.
        var marker = new EventEnvelope<>(UUID.randomUUID(), TemplatePingV1.TYPE, 1, null, Instant.now(), "template",
                "ping", null, new TemplatePingV1("dup-marker"));
        rabbit.send("flavors.events", TemplatePingV1.TYPE,
                MessageBuilder.withBody(codec.encode(marker)).andProperties(props).build());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(received("dup-marker")).isEqualTo(1));
        assertThat(received("dup")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox WHERE message_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void theOutboxEventRollsBackWithTheBusinessChange() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            outbox.write("template.ping", 1, "template", "ping", null, new TemplatePingV1("never-happened"));
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE payload->>'note' = 'never-happened'",
                Integer.class)).isZero();
    }

    @Test
    void theOutboxWriterRequiresATransaction() {
        assertThatThrownBy(() -> outbox.write("template.ping", 1, "template", "ping", null,
                new TemplatePingV1("no-tx"))).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theInboxRunsTheWorkOnceAndLeavesNothingWhenItFails() {
        UUID id = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> inbox.runOnce("c", id, "t", () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("handler failed");
        })).isInstanceOf(IllegalStateException.class);
        boolean first = inbox.runOnce("c", id, "t", runs::incrementAndGet); // the retry runs it
        boolean duplicate = inbox.runOnce("c", id, "t", runs::incrementAndGet);
        boolean otherConsumer = inbox.runOnce("c2", id, "t", runs::incrementAndGet);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(otherConsumer).isTrue();
        assertThat(runs).hasValue(3);
    }

    @Test
    void theRelayKeepsOutboxOrder() {
        for (int i = 0; i < 5; i++) {
            int n = i;
            tx.executeWithoutResult(status -> outbox.write("template.ping", 1, "template", "order",
                    (long) n, new TemplatePingV1("order-" + n)));
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = 'order' AND published_at IS NULL",
                Integer.class)).isZero());
        var publishedOrder = jdbc.queryForList(
                "SELECT state_version FROM outbox WHERE aggregate_id = 'order' ORDER BY published_at, seq",
                Long.class);
        assertThat(publishedOrder).containsExactly(0L, 1L, 2L, 3L, 4L);
    }
}
