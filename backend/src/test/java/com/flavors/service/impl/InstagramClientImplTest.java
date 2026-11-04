package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The parts of the Instagram client that need no network. The calls to Instagram itself can only be checked
 * against the real Meta app (see the pull request).
 */
class InstagramClientImplTest {

    private static final String REDIRECT = "https://flavors.test/api/creator/social/instagram/callback";

    @Test
    void theLoginPageAsksForBothPermissionsAndCarriesTheState() {
        InstagramClientImpl client = new InstagramClientImpl("123456", "top-secret", REDIRECT);

        String url = client.authorizationUrl("state-abc");

        UriComponents parts = UriComponentsBuilder.fromUriString(url).build();
        assertThat(parts.getScheme()).isEqualTo("https");
        assertThat(parts.getHost()).isEqualTo("www.instagram.com");
        assertThat(parts.getPath()).isEqualTo("/oauth/authorize");
        MultiValueMap<String, String> query = parts.getQueryParams();
        assertThat(decoded(query.getFirst("client_id"))).isEqualTo("123456");
        assertThat(decoded(query.getFirst("redirect_uri"))).isEqualTo(REDIRECT);
        assertThat(decoded(query.getFirst("response_type"))).isEqualTo("code");
        assertThat(decoded(query.getFirst("scope")))
                .isEqualTo("instagram_business_basic,instagram_business_manage_insights");
        assertThat(decoded(query.getFirst("state"))).isEqualTo("state-abc");
        // The secret is only ever sent from the server, never through the browser.
        assertThat(url).doesNotContain("top-secret");
    }

    @Test
    void isConfiguredOnlyWithTheIdTheSecretAndTheRedirectUrl() {
        assertThat(new InstagramClientImpl("", "", "").isConfigured()).isFalse();
        assertThat(new InstagramClientImpl("id", "secret", "").isConfigured()).isFalse();
        assertThat(new InstagramClientImpl("id", "", REDIRECT).isConfigured()).isFalse();
        assertThat(new InstagramClientImpl("", "secret", REDIRECT).isConfigured()).isFalse();
        assertThat(new InstagramClientImpl("id", "secret", REDIRECT).isConfigured()).isTrue();
    }

    private static String decoded(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
