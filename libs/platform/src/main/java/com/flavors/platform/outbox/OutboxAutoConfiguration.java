package com.flavors.platform.outbox;

import java.time.Clock;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Outbox writer, the RabbitMQ exchange, and the relay that publishes the outbox on a timer. */
@AutoConfiguration(afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        return new OutboxWriter(jdbc, json, clock);
    }

    /** One topic exchange for all domain events; declared by Spring's RabbitAdmin on first connect. */
    @Bean
    @ConditionalOnMissingBean(name = "flavorsEventsExchange")
    TopicExchange flavorsEventsExchange(OutboxProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "flavors.platform.outbox", name = "relay-enabled", havingValue = "true",
            matchIfMissing = true)
    @EnableScheduling
    static class RelayConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                RabbitTemplate rabbit, EventCodec codec, JsonMapper json,
                                OutboxProperties properties) {
            return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), rabbit, codec, json,
                    properties);
        }

        @Bean
        OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
            return new OutboxRelayScheduler(relay);
        }
    }

    /** Runs the relay every {@code flavors.platform.outbox.poll-interval} (default 1s) after the last run ends. */
    static class OutboxRelayScheduler {

        private final OutboxRelay relay;

        OutboxRelayScheduler(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${flavors.platform.outbox.poll-interval:1s}")
        void run() {
            relay.publishPending();
        }
    }
}
