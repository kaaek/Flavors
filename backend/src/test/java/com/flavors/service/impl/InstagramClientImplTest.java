package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.exception.SocialPlatformException;
import com.flavors.exception.SocialTokenInvalidException;
import com.flavors.service.InstagramClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The Instagram client (Unified Login, EEC-88) against a local stand-in for graph.facebook.com, so the requests it
 * sends and how it reads the answers are checked without Meta. Whether Meta really answers this way can only be
 * checked with the real Meta app (README).
 */
class InstagramClientImplTest {

    private static final String REDIRECT = "https://flavors.test/api/creator/social/instagram/callback";

    private HttpServer graph;
    /** Every request the stand-in received, by path (without the query), in order: the full URI with its query. */
    private final List<String> requests = new ArrayList<>();
    /** What the stand-in answers per path: status and JSON. */
    private final Map<String, Object[]> answers = new ConcurrentHashMap<>();
    /** The Content-Type the stand-in sends; graph.facebook.com really sends text/javascript. */
    private String contentType = "application/json";

    @AfterEach
    void stopStandIn() {
        if (graph != null) {
            graph.stop(0);
        }
    }

    // ---- The login page and the settings ----

    @Test
    void theLoginPageCarriesTheConfigurationAndTheState() {
        InstagramClientImpl client = new InstagramClientImpl("123456", "top-secret", "98765", REDIRECT, "http://unused");

        String url = client.authorizationUrl("state-abc");

        UriComponents parts = UriComponentsBuilder.fromUriString(url).build();
        assertThat(parts.getScheme()).isEqualTo("https");
        assertThat(parts.getHost()).isEqualTo("www.facebook.com");
        assertThat(parts.getPath()).isEqualTo("/dialog/oauth");
        MultiValueMap<String, String> query = parts.getQueryParams();
        assertThat(decoded(query.getFirst("client_id"))).isEqualTo("123456");
        assertThat(decoded(query.getFirst("config_id"))).isEqualTo("98765");
        assertThat(decoded(query.getFirst("redirect_uri"))).isEqualTo(REDIRECT);
        assertThat(decoded(query.getFirst("response_type"))).isEqualTo("code");
        assertThat(decoded(query.getFirst("state"))).isEqualTo("state-abc");
        // The permissions come from the configuration, not the URL; and the secret never goes through the browser.
        assertThat(query.getFirst("scope")).isNull();
        assertThat(url).doesNotContain("top-secret");
    }

    @Test
    void isConfiguredOnlyWithTheIdTheSecretTheConfigurationAndTheRedirectUrl() {
        assertThat(client("", "", "", "").isConfigured()).isFalse();
        assertThat(client("id", "secret", "config", "").isConfigured()).isFalse();
        assertThat(client("id", "secret", "", REDIRECT).isConfigured()).isFalse();
        assertThat(client("id", "", "config", REDIRECT).isConfigured()).isFalse();
        assertThat(client("", "secret", "config", REDIRECT).isConfigured()).isFalse();
        assertThat(client("id", "secret", "config", REDIRECT).isConfigured()).isTrue();
    }

    // ---- Code exchange ----

    @Test
    void theCodeIsTradedForATokenInOneServerSideCall() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/oauth/access_token", 200, "{\"access_token\":\"system-token\",\"token_type\":\"bearer\"}");

        assertThat(client.exchangeCode("the-code")).isEqualTo("system-token");

        assertThat(requests).singleElement().satisfies(r -> assertThat(r)
                .startsWith("/oauth/access_token?").contains("client_id=app-id")
                .contains("client_secret=app-secret").contains("code=the-code")
                .contains("redirect_uri=" + REDIRECT.replace(":", "%3A").replace("/", "%2F")));
    }

    @Test
    void aRefusedCodeIsAPlainPlatformErrorWithoutSecretsInIt() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/oauth/access_token", 400, "{\"error\":{\"message\":\"Code was used\",\"code\":100}}");

        assertThatThrownBy(() -> client.exchangeCode("the-code"))
                .isInstanceOf(SocialPlatformException.class)
                .isNotInstanceOf(SocialTokenInvalidException.class)
                .hasMessageNotContaining("app-secret").hasMessageNotContaining("the-code")
                // Enough to find the cause in a log: which call, the status, and Meta's error numbers (not its text).
                .hasMessageContaining("code exchange").hasMessageContaining("status 400")
                .hasMessageContaining("code 100").hasMessageNotContaining("Code was used");
    }

    @Test
    void readsAnAnswerSentAsTextJavascriptLikeGraphFacebookDoes() throws IOException {
        contentType = "text/javascript; charset=UTF-8";
        InstagramClientImpl client = standIn();
        answer("/me/assigned_instagram_accounts", 200, "{\"data\":[{\"id\":\"1784\",\"username\":\"lara.eats\"}]}");

        assertThat(client.assignedAccounts("tok")).containsExactly(new InstagramClient.AccountRef("1784", "lara.eats"));
    }

    @Test
    void anAnswerThatIsNotJsonIsAPlatformErrorNamingTheCall() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/me/assigned_instagram_accounts", 200, "<html>maintenance</html>");

        assertThatThrownBy(() -> client.assignedAccounts("tok"))
                .isInstanceOf(SocialPlatformException.class)
                .hasMessageContaining("account list").hasMessageContaining("not readable JSON")
                .hasMessageNotContaining("maintenance");
    }

    // ---- Which account ----

    @Test
    void theSharedAccountsAreListedWithTheirIdsAndUsernames() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/me/assigned_instagram_accounts", 200,
                "{\"data\":[{\"id\":\"1784\",\"username\":\"lara.eats\"},{\"id\":\"1785\"}]}");

        assertThat(client.assignedAccounts("tok")).containsExactly(
                new InstagramClient.AccountRef("1784", "lara.eats"), new InstagramClient.AccountRef("1785", null));
        assertThat(requests).singleElement().satisfies(r -> assertThat(r).contains("fields=id,username").contains("access_token=tok"));
    }

    @Test
    void noSharedAccountIsAnEmptyList() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/me/assigned_instagram_accounts", 200, "{\"data\":[]}");

        assertThat(client.assignedAccounts("tok")).isEmpty();
    }

    // ---- The pull ----

    @Test
    void aPullReadsTheNamedAccountsFollowersAndPostFigures() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/1784", 200, "{\"username\":\"lara.eats\",\"followers_count\":12000,\"id\":\"1784\"}");
        answer("/1784/media", 200, "{\"data\":[{\"id\":\"p1\",\"like_count\":10,\"comments_count\":2}]}");
        answer("/p1/insights", 200, "{\"data\":[{\"name\":\"views\",\"values\":[{\"value\":5000}]},"
                + "{\"name\":\"likes\",\"values\":[{\"value\":400}]},{\"name\":\"comments\",\"values\":[{\"value\":50}]},"
                + "{\"name\":\"shares\",\"total_value\":{\"value\":30}}]}");

        InstagramClient.Snapshot snapshot = client.fetchSnapshot("tok", "1784");

        assertThat(snapshot.externalId()).isEqualTo("1784");
        assertThat(snapshot.username()).isEqualTo("lara.eats");
        assertThat(snapshot.followers()).isEqualTo(12000);
        assertThat(snapshot.posts()).containsExactly(new InstagramClient.PostFigures("p1", 5000, 400, 50, 30));
        assertThat(requests).anySatisfy(r -> assertThat(r).startsWith("/1784?").contains("followers_count"));
    }

    @Test
    void aPostWithoutInsightsKeepsItsLikeAndCommentCounts() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/1784", 200, "{\"username\":\"lara.eats\",\"followers_count\":100}");
        answer("/1784/media", 200, "{\"data\":[{\"id\":\"p1\",\"like_count\":10,\"comments_count\":2}]}");
        answer("/p1/insights", 400, "{\"error\":{\"message\":\"Not available\",\"code\":100}}");

        assertThat(client.fetchSnapshot("tok", "1784").posts())
                .containsExactly(new InstagramClient.PostFigures("p1", 0, 10, 2, 0));
    }

    @Test
    void aRevokedTokenIsReportedAsInvalidEvenWhenOnlyTheInsightsCallSeesIt() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/1784", 200, "{\"username\":\"lara.eats\",\"followers_count\":100}");
        answer("/1784/media", 200, "{\"data\":[{\"id\":\"p1\"}]}");
        answer("/p1/insights", 400, "{\"error\":{\"message\":\"Invalid OAuth access token.\",\"code\":190}}");

        assertThatThrownBy(() -> client.fetchSnapshot("tok", "1784")).isInstanceOf(SocialTokenInvalidException.class);
    }

    @Test
    void anyOtherRefusalIsAPlainPlatformErrorWithoutTheTokenInIt() throws IOException {
        InstagramClientImpl client = standIn();
        answer("/1784", 500, "{\"error\":{\"message\":\"Temporary\",\"code\":2}}");

        assertThatThrownBy(() -> client.fetchSnapshot("secret-token", "1784"))
                .isInstanceOf(SocialPlatformException.class)
                .isNotInstanceOf(SocialTokenInvalidException.class)
                .hasMessageNotContaining("secret-token");
    }

    // ---- Helpers ----

    private static InstagramClientImpl client(String id, String secret, String config, String redirect) {
        return new InstagramClientImpl(id, secret, config, redirect, "http://unused");
    }

    private void answer(String path, int status, String json) {
        answers.put(path, new Object[] {status, json});
    }

    /** A client pointed at a local server that answers what was set with {@link #answer}. */
    private InstagramClientImpl standIn() throws IOException {
        graph = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        graph.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            Object[] answer = answers.get(exchange.getRequestURI().getPath());
            int status = answer == null ? 404 : (int) answer[0];
            byte[] body = (answer == null ? "{}" : (String) answer[1]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        graph.start();
        return new InstagramClientImpl("app-id", "app-secret", "config-id", REDIRECT,
                "http://127.0.0.1:" + graph.getAddress().getPort());
    }

    private static String decoded(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
