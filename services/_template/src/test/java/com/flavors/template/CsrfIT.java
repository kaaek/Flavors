package com.flavors.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The double-submit cookie setup equivalent to the monolith's {@code csrf.spa()}. */
class CsrfIT extends IntegrationTestBase {

    private static final String BODY = "{\"note\":\"csrf\"}";

    private String xsrfCookieValue(Response response) {
        return response.setCookies().stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';'))).findFirst().orElseThrow();
    }

    @Test
    void cookieAuthenticatedWriteWithoutCsrfTokenIsForbidden() {
        String cookie = "flavors_access=" + token(5, "BRAND", null);

        Response response = postJson("/api/template/ping", Map.of("Cookie", cookie), BODY);

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void doubleSubmitWorksAndAMismatchIsRefused() {
        String access = "flavors_access=" + token(5, "BRAND", null);
        // Any response hands the SPA its XSRF-TOKEN cookie (readable by JavaScript, unlike the access cookie).
        Response first = send("GET", "/api/template/whoami", Map.of("Cookie", access), null);
        String xsrf = xsrfCookieValue(first);
        assertThat(first.setCookies().stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow())
                .doesNotContainIgnoringCase("HttpOnly");

        Response ok = postJson("/api/template/ping",
                Map.of("Cookie", access + "; XSRF-TOKEN=" + xsrf, "X-XSRF-TOKEN", xsrf), BODY);
        Response mismatch = postJson("/api/template/ping",
                Map.of("Cookie", access + "; XSRF-TOKEN=" + xsrf, "X-XSRF-TOKEN", "forged"), BODY);

        assertThat(ok.status()).isEqualTo(200);
        assertThat(mismatch.status()).isEqualTo(403);
    }

    @Test
    void serviceToServiceCallsWithABearerHeaderAreNotCsrfChecked() {
        Response response = postJson("/api/template/ping",
                Map.of("Authorization", "Bearer " + token(5, "BRAND", null)), BODY);

        assertThat(response.status()).isEqualTo(200);
    }
}
