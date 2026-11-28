package com.flavors.platform.config;

import com.flavors.platform.audit.AuditLogger;
import com.flavors.platform.audit.JdbcAuditLogger;
import com.flavors.platform.idempotency.IdempotencyService;
import com.flavors.platform.idempotency.JdbcIdempotencyService;
import com.flavors.platform.inbox.InboxService;
import com.flavors.platform.outbox.EventCodec;
import com.flavors.platform.web.CorrelationIdFilter;
import com.flavors.platform.web.PlatformExceptionHandler;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers the platform beans that need no web or broker: clock, audit, idempotency, inbox, event codec.
 * Every bean backs off when a service defines its own, which is also how tests substitute a fixed clock.
 */
@AutoConfiguration
public class PlatformAutoConfiguration {

    /** One UTC clock to inject instead of calling {@code Instant.now()}, so tests can fix the time. */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    AuditLogger auditLogger(JdbcTemplate jdbc, JsonMapper json) {
        return new JdbcAuditLogger(jdbc, json);
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotencyService idempotencyService(JdbcTemplate jdbc, JsonMapper json) {
        return new JdbcIdempotencyService(jdbc, json);
    }

    @Bean
    @ConditionalOnMissingBean
    InboxService inboxService(JdbcTemplate jdbc) {
        return new InboxService(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    EventCodec eventCodec(JsonMapper json) {
        return new EventCodec(json);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformExceptionHandler platformExceptionHandler() {
        return new PlatformExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
