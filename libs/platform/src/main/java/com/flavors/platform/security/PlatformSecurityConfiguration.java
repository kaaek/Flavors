package com.flavors.platform.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * JWT resource-server setup shared by every service: who may call what. Ported from the monolith's
 * {@code SecurityConfig} (docs/Architecture.md §5), with the session replaced by a short-lived token that
 * identity issues. Switch the whole chain off with {@code flavors.platform.security.default-filter-chain=false}
 * when a service needs different rules, and reuse {@link #configure} from its own chain.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PlatformSecurityProperties.class)
// Lets features add finer checks on methods, e.g. "this campaign belongs to the current brand" (NF-1).
@EnableMethodSecurity
public class PlatformSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder platformJwtDecoder(PlatformSecurityProperties properties) {
        if (properties.jwksUri() == null || properties.jwksUri().isBlank()) {
            // Like the PG* variables: a missing value fails startup instead of leaving the service open.
            throw new IllegalStateException("flavors.platform.security.jwks-uri must point at identity's JWKS URL");
        }
        return PlatformJwt.decoder(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailSecurityHandler problemDetailSecurityHandler(JsonMapper jsonMapper) {
        return new ProblemDetailSecurityHandler(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnProperty(prefix = "flavors.platform.security", name = "default-filter-chain",
            havingValue = "true", matchIfMissing = true)
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, PlatformSecurityProperties properties,
                                                    ProblemDetailSecurityHandler errors,
                                                    JwtDecoder jwtDecoder) throws Exception {
        configure(http, properties, errors, jwtDecoder);
        return http.build();
    }

    /** The platform rules, for a service that builds its own chain but wants the same behaviour. */
    public static void configure(HttpSecurity http, PlatformSecurityProperties properties,
                                 ProblemDetailSecurityHandler errors, JwtDecoder jwtDecoder) throws Exception {
        http
                // Rules are checked top to bottom; the first matching one wins.
                .authorizeHttpRequests(auth -> auth
                        // Diner pages: storefront, offer claim, QR page. No account needed.
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/creator/**").hasRole(Role.CREATOR.name())
                        .requestMatchers("/api/brand/**").hasRole(Role.BRAND.name())
                        .requestMatchers("/api/admin/**").hasAuthority(CurrentUser.ADMIN_MFA_AUTHORITY)
                        // Shared authenticated APIs (e.g. /api/me) also serve admins. A password-only
                        // admin token (no mfa claim) must never use these paths to bypass the second factor.
                        .requestMatchers("/api/**").access((authentication, request) -> {
                            var user = authentication.get();
                            boolean signedIn = user != null && user.isAuthenticated()
                                    && !(user instanceof AnonymousAuthenticationToken);
                            boolean pendingAdmin = signedIn && user.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals(Role.ADMIN.authority()))
                                    && user.getAuthorities().stream()
                                    .noneMatch(a -> a.getAuthority().equals(CurrentUser.ADMIN_MFA_AUTHORITY));
                            return new AuthorizationDecision(signedIn && !pendingAdmin);
                        })
                        // Kubernetes probes and the CI smoke test. Other actuator endpoints are not exposed;
                        // denying them guards against exposing one by mistake.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        // Internal service-to-service APIs and anything else: a valid token is required.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(PlatformJwt.converter())))
                // 401/403 as JSON instead of a redirect to a login page.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                // CSRF stays on, exactly like the monolith: the token cookie is sent automatically by the
                // browser, so without a check another site could make a logged-in user's browser send
                // requests. SPA mode puts the token in a cookie (XSRF-TOKEN) that the frontend reads and
                // sends back in the X-XSRF-TOKEN header (double submit).
                // (Spring skips the check for requests with an Authorization: Bearer header, which another site
                // cannot forge; the cookie token is turned into that header only after this check, see
                // CookieTokenFilter.)
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CookieTokenFilter(properties.accessCookieName()), CsrfFilter.class)
                // Stateless: the token is the login. No session means no database row per user.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
    }
}
