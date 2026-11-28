package com.flavors.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Builds the JWT decoder (JWKS-based) and the claims-to-{@link CurrentUser} converter. */
public final class PlatformJwt {

    private PlatformJwt() {
    }

    /** Validates signature against identity's JWKS, expiry, optional issuer, and the Flavors claims. */
    public static JwtDecoder decoder(PlatformSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (properties.issuer() != null && !properties.issuer().isBlank()) {
            validators.add(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        } else {
            validators.add(JwtValidators.createDefault());
        }
        validators.add(PlatformJwt::validateFlavorsClaims);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /** {@code sub} must be a user id and {@code role} one of the known roles; anything else is not ours. */
    static OAuth2TokenValidatorResult validateFlavorsClaims(Jwt jwt) {
        try {
            CurrentUser.fromJwt(jwt);
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException e) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "The token needs a numeric sub and a known role claim", null));
        }
    }

    public static Converter<Jwt, AbstractAuthenticationToken> converter() {
        return jwt -> new CurrentUserAuthentication(CurrentUser.fromJwt(jwt), jwt);
    }
}
