package com.flavors.platform.outbox;

import com.flavors.contracts.EventEnvelope;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** Turns an {@link EventEnvelope} into the JSON that travels on the broker and back. */
public class EventCodec {

    private final JsonMapper json;

    public EventCodec(JsonMapper json) {
        this.json = json;
    }

    public byte[] encode(EventEnvelope<?> envelope) {
        return json.writeValueAsBytes(envelope);
    }

    /** @param payloadType the payload record of the event type being consumed, e.g. {@code TemplatePingV1.class} */
    public <T> EventEnvelope<T> decode(byte[] body, Class<T> payloadType) {
        JavaType type = json.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
        return json.readValue(body, type);
    }
}
