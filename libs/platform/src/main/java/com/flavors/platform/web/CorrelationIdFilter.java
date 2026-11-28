package com.flavors.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Takes the gateway's request id ({@code X-Correlation-Id}, else {@code X-Request-Id}) or makes one, puts it
 * in the logging MDC and echoes it back. Outbox events and audit details carry it, so one user action can
 * be followed across services (EEC-95 §1.3).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = firstNonBlank(request.getHeader(CorrelationContext.HEADER), request.getHeader("X-Request-Id"));
        if (id == null || id.length() > 100) {
            id = UUID.randomUUID().toString();
        }
        CorrelationContext.set(id);
        response.setHeader(CorrelationContext.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }
}
