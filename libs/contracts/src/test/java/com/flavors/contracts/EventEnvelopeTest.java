package com.flavors.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flavors.contracts.events.v1.TemplatePingV1;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void carriesTheMetadataConsumersDedupeAndOrderOn() {
        UUID id = UUID.randomUUID();
        var envelope = new EventEnvelope<>(id, TemplatePingV1.TYPE, TemplatePingV1.VERSION, "corr-1",
                Instant.parse("2026-11-28T10:00:00Z"), "template", "42", 7L, new TemplatePingV1("hi"));

        assertEquals(id, envelope.messageId());
        assertEquals("template.ping", envelope.type());
        assertEquals(7L, envelope.stateVersion());
        assertEquals("hi", envelope.payload().note());
    }

    @Test
    void rejectsAnEnvelopeThatCannotBeDeduplicated() {
        assertThrows(NullPointerException.class, () -> new EventEnvelope<>(null, "t", 1, null,
                Instant.now(), "a", "1", null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope<>(UUID.randomUUID(), "t", 0, null,
                Instant.now(), "a", "1", null, "x"));
    }
}
