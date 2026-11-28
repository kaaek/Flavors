package com.flavors.platform.web;

import org.slf4j.MDC;

/** The correlation id of the request being handled, shared through the logging MDC. */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationContext() {
    }

    /** The current correlation id, or null outside a request (jobs, consumers that did not set one). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
