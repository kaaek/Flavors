package com.flavors.template;

import com.flavors.contracts.events.v1.TemplatePingV1;
import com.flavors.platform.audit.AuditLogger;
import com.flavors.platform.outbox.OutboxWriter;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The pattern every state change follows: business change + audit row + outbox event, one transaction. */
@Service
class TemplatePingService {

    record PingResult(UUID messageId) {
    }

    private final AuditLogger audit;
    private final OutboxWriter outbox;

    TemplatePingService(AuditLogger audit, OutboxWriter outbox) {
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    PingResult ping(String note) {
        // (a real service changes its own tables here)
        audit.record("TEMPLATE_PING", "template", null, Map.of("note", note));
        UUID messageId = outbox.write(TemplatePingV1.TYPE, TemplatePingV1.VERSION, "template", "ping", null,
                new TemplatePingV1(note));
        return new PingResult(messageId);
    }
}
