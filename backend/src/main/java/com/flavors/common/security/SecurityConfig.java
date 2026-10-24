package com.flavors.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who may call what. See docs/Architecture.md §5.
 *
 * <p>Login, logout and password reset endpoints belong to UC-F19.3; this class only sets up the
 * rules, the error responses and the session/CSRF mechanism they rely on.
 */
@Configuration(proxyBeanMethods = false)
// Lets features add finer checks on methods, e.g. "this campaign belongs to the current brand"
// (NF-1) with @PreAuthorize.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) {
        ProblemDetailSecurityHandler errors = new ProblemDetailSecurityHandler(jsonMapper);

        http
                // Rules are checked top to bottom; the first matching one wins.
                .authorizeHttpRequests(auth -> auth
                        // Diner pages: storefront, offer claim, QR page. No account needed.
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/creator/**").hasRole(Role.CREATOR.name())
                        .requestMatchers("/api/brand/**").hasRole(Role.BRAND.name())
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        // Anything else under /api (e.g. /api/me) needs a login, whatever the role.
                        .requestMatchers("/api/**").authenticated()
                        // Railway's health check and the CI smoke test. Other actuator endpoints
                        // aren't exposed (application.yml); denying them guards against exposing one by mistake.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        // The SPA's files and client-side routes: the SPA itself checks the role (RequireRole).
                        .anyRequest().permitAll())
                // 401/403 as JSON instead of a redirect to a login page.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                // CSRF stays on: the session cookie is sent automatically, so without it another site
                // could make a logged-in user's browser send requests. SPA mode puts the token in a
                // cookie (XSRF-TOKEN) that the frontend reads and sends back in the X-XSRF-TOKEN header.
                .csrf(csrf -> csrf.spa())
                // No login form, no HTTP Basic, no default /logout page: the SPA and UC-F19.3 do these.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // The request cache remembers a denied request to redirect back to after login. The SPA
                // doesn't need that, and saving it would create a session (a database row) for every
                // anonymous 401.
                .requestCache(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Hashes passwords with BCrypt. The delegating encoder stores the algorithm with the hash
     * ({@code {bcrypt}$2a$...}), so it can be changed later without breaking existing passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
