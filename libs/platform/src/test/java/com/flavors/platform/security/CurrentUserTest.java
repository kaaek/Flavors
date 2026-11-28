package com.flavors.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    private static Jwt jwt(String sub, String role, Object mfa) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (mfa != null) {
            builder.claim("mfa", mfa);
        }
        return builder.build();
    }

    private static List<String> authorities(Jwt jwt) {
        return CurrentUser.fromJwt(jwt).authorities().stream().map(Object::toString).toList();
    }

    @Test
    void adminGetsAdminMfaOnlyWhenTheMfaClaimIsSet() {
        assertEquals(List.of("ROLE_ADMIN"), authorities(jwt("1", "ADMIN", null)));
        assertEquals(List.of("ROLE_ADMIN"), authorities(jwt("1", "ADMIN", false)));
        assertEquals(List.of("ROLE_ADMIN", "ROLE_ADMIN_MFA"), authorities(jwt("1", "ADMIN", true)));
    }

    @Test
    void theMfaClaimNeverElevatesANonAdmin() {
        assertEquals(List.of("ROLE_BRAND"), authorities(jwt("2", "BRAND", true)));
        assertEquals(List.of("ROLE_CREATOR"), authorities(jwt("3", "CREATOR", true)));
    }

    @Test
    void claimsBecomeTheCurrentUser() {
        CurrentUser user = CurrentUser.fromJwt(jwt("42", "BRAND", null));
        assertEquals(42L, user.id());
        assertEquals(Role.BRAND, user.role());
        assertEquals("user:42", user.getUsername());
    }

    @Test
    void tokensThatAreNotOursAreRejected() {
        assertTrue(PlatformJwt.validateFlavorsClaims(jwt("42", "BRAND", null)).getErrors().isEmpty());
        assertFalse(PlatformJwt.validateFlavorsClaims(jwt("not-a-number", "BRAND", null)).getErrors().isEmpty());
        assertFalse(PlatformJwt.validateFlavorsClaims(jwt("42", "SUPERUSER", null)).getErrors().isEmpty());
        assertFalse(PlatformJwt.validateFlavorsClaims(jwt("42", null, null)).getErrors().isEmpty());
    }
}
