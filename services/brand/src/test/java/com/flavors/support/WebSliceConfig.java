package com.flavors.support;

import com.flavors.exception.BrandExceptionHandler;
import com.flavors.platform.security.PlatformSecurityConfiguration;
import com.flavors.platform.web.PlatformExceptionHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * What a {@code @WebMvcTest} of a brand controller needs: the platform's JWT security rules (the same path rules
 * the monolith's {@code SecurityConfig} had), the platform and brand error handlers, and a decoder that rejects
 * every real token, because slice tests sign nobody in with a token: they use {@link TestUsers#user}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({PlatformSecurityConfiguration.class, PlatformExceptionHandler.class, BrandExceptionHandler.class})
public class WebSliceConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new BadJwtException("slice tests do not decode tokens");
        };
    }
}
