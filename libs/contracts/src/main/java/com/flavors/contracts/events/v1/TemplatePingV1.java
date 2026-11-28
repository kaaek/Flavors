package com.flavors.contracts.events.v1;

/**
 * Placeholder event used by {@code services/_template} to prove the outbox and inbox work end to end.
 * It carries no business meaning. Real events (campaign, payment, ...) are added here by the service
 * that owns them, after captain review; the money-path events come from the saga design document.
 */
public record TemplatePingV1(String note) {

    public static final String TYPE = "template.ping";
    public static final int VERSION = 1;
}
