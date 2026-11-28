package com.flavors.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets the browser SPA authenticate with the HttpOnly access-token cookie: when a request has no
 * {@code Authorization} header, the cookie's value is presented to the resource-server filter as
 * {@code Authorization: Bearer <token>}.
 *
 * <p>It runs <b>after</b> the CSRF filter on purpose. Spring's resource-server support skips the CSRF check
 * for every request that carries a bearer token in the header (such a request cannot be forged by another
 * site). The cookie is sent by the browser automatically, so a cookie-authenticated request must still be
 * checked; the CSRF filter therefore has to see it as it arrived, without the synthetic header.
 */
public final class CookieTokenFilter extends OncePerRequestFilter {

    private final String cookieName;

    public CookieTokenFilter(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader("Authorization") == null ? cookieToken(request) : null;
        chain.doFilter(token == null ? request : new WithBearer(request, "Bearer " + token), response);
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookieName) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static final class WithBearer extends HttpServletRequestWrapper {

        private final String authorization;

        WithBearer(HttpServletRequest request, String authorization) {
            super(request);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return "Authorization".equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return "Authorization".equalsIgnoreCase(name)
                    ? Collections.enumeration(Set.of(authorization)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
            names.add("Authorization");
            return Collections.enumeration(names);
        }
    }
}
