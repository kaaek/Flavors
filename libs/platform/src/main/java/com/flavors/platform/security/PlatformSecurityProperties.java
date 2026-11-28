package com.flavors.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code flavors.platform.security.*}. Identity signs the tokens; every other service only needs to know
 * where identity publishes its public keys.
 *
 * @param jwksUri               identity's JWKS URL, e.g. {@code http://identity:8080/.well-known/jwks.json}
 *                              (required: a missing value fails startup, like the PG* variables)
 * @param issuer                expected {@code iss} claim; not checked when blank
 * @param accessCookieName      the HttpOnly cookie that carries the access token from the browser
 * @param defaultFilterChain    set false when a service defines its own {@code SecurityFilterChain}
 */
@ConfigurationProperties("flavors.platform.security")
public record PlatformSecurityProperties(
        String jwksUri,
        String issuer,
        String accessCookieName,
        Boolean defaultFilterChain) {

    public PlatformSecurityProperties {
        if (accessCookieName == null || accessCookieName.isBlank()) {
            accessCookieName = "flavors_access";
        }
        if (defaultFilterChain == null) {
            defaultFilterChain = true;
        }
    }
}
