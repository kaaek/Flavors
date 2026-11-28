package com.flavors.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JWT validation against identity's JWKS, and the role rules ported from the monolith. */
class JwtSecurityIT extends IntegrationTestBase {

    @Test
    void noTokenIs401AsProblemJson() {
        Response response = get("/api/template/whoami", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(
                type -> assertThat(type).contains("application/problem+json"));
        assertThat(response.body()).contains("\"status\":401");
    }

    @Test
    void validTokenBecomesTheCurrentUserFromItsClaims() {
        Response response = get("/api/template/whoami", token(42, "BRAND", null));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":42").contains("\"role\":\"BRAND\"").contains("ROLE_BRAND");
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() {
        String forged = token(OTHER_KEY, 42, "BRAND", null, Instant.now().plusSeconds(300), b -> { });

        assertThat(get("/api/template/whoami", forged).status()).isEqualTo(401);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = token(SIGNING_KEY, 42, "BRAND", null, Instant.now().minusSeconds(120), b -> { });

        assertThat(get("/api/template/whoami", expired).status()).isEqualTo(401);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = token(SIGNING_KEY, 42, "BRAND", null, Instant.now().plusSeconds(300),
                b -> b.issuer("http://evil.test"));

        assertThat(get("/api/template/whoami", foreign).status()).isEqualTo(401);
    }

    @Test
    void tokenWithoutAKnownRoleIsRejected() {
        assertThat(get("/api/template/whoami", token(42, null, null)).status()).isEqualTo(401);
        assertThat(get("/api/template/whoami", token(42, "SUPERUSER", null)).status()).isEqualTo(401);
    }

    @Test
    void roleGuardsKeepTheMonolithsRules() {
        assertThat(get("/api/brand/anything", token(1, "CREATOR", null)).status()).isEqualTo(403);
        assertThat(get("/api/creator/anything", token(1, "BRAND", null)).status()).isEqualTo(403);
        assertThat(get("/api/admin/template/ping", token(1, "BRAND", true)).status()).isEqualTo(403);
    }

    @Test
    void adminNeedsTheMfaClaimForAdminPathsAndSharedPaths() {
        String passwordOnly = token(9, "ADMIN", null);
        String withMfa = token(9, "ADMIN", true);

        assertThat(get("/api/admin/template/ping", passwordOnly).status()).isEqualTo(403);
        assertThat(get("/api/template/whoami", passwordOnly).status()).isEqualTo(403); // pending admin
        assertThat(get("/api/admin/template/ping", withMfa).status()).isEqualTo(200);
        assertThat(get("/api/template/whoami", withMfa).body()).contains("ROLE_ADMIN_MFA");
    }

    @Test
    void theMfaClaimDoesNotElevateANonAdmin() {
        assertThat(get("/api/admin/template/ping", token(2, "CREATOR", true)).status()).isEqualTo(403);
    }

    @Test
    void theBrowserTokenCookieWorksLikeTheHeader() {
        Response response = send("GET", "/api/template/whoami",
                Map.of("Cookie", "flavors_access=" + token(7, "CREATOR", null)), null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":7");
    }

    @Test
    void healthProbesArePublicAndOtherActuatorEndpointsAreNot() {
        assertThat(get("/actuator/health/liveness", null).body()).contains("UP");
        assertThat(get("/actuator/health/readiness", null).body()).contains("UP");
        assertThat(get("/actuator/env", token(9, "ADMIN", true)).status()).isIn(403, 404);
    }
}
