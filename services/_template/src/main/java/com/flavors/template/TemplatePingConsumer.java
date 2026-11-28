package com.flavors.template;

import com.flavors.contracts.EventEnvelope;
import com.flavors.contracts.events.v1.TemplatePingV1;
import com.flavors.platform.audit.AuditLogger;
import com.flavors.platform.inbox.InboxService;
import com.flavors.platform.outbox.EventCodec;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * How a service consumes an event: a durable queue bound to the shared exchange, and a handler that wraps
 * its work in {@link InboxService#runOnce} so a redelivered message is a no-op. The sample handler just
 * writes an audit row (as {@code system}: nobody is logged in on a consumer).
 */
@Component
class TemplatePingConsumer {

    static final String QUEUE = "template-service.template.ping";

    @Configuration(proxyBeanMethods = false)
    static class Topology {

        @Bean
        Queue templatePingQueue() {
            return new Queue(QUEUE, true);
        }

        @Bean
        Binding templatePingBinding(Queue templatePingQueue, TopicExchange flavorsEventsExchange) {
            return BindingBuilder.bind(templatePingQueue).to(flavorsEventsExchange).with(TemplatePingV1.TYPE);
        }
    }

    private final EventCodec codec;
    private final InboxService inbox;
    private final AuditLogger audit;

    TemplatePingConsumer(EventCodec codec, InboxService inbox, AuditLogger audit) {
        this.codec = codec;
        this.inbox = inbox;
        this.audit = audit;
    }

    @RabbitListener(queues = QUEUE)
    void onPing(Message message) {
        EventEnvelope<TemplatePingV1> event = codec.decode(message.getBody(), TemplatePingV1.class);
        inbox.runOnce(QUEUE, event.messageId(), event.type(), () ->
                audit.record("TEMPLATE_PING_RECEIVED", "template", null,
                        Map.of("messageId", event.messageId().toString(), "note", event.payload().note())));
    }
}
