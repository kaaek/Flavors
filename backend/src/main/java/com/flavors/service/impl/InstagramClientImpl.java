package com.flavors.service.impl;

import com.flavors.exception.SocialPlatformException;
import com.flavors.exception.SocialTokenInvalidException;
import com.flavors.service.InstagramClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to the Instagram API with Unified Login (EEC-88): the creator logs in on Meta's page, picks her business
 * portfolio and Instagram account, and Meta returns a system-user token for the assets she shared. It needs five
 * settings from the Meta app (README): {@code FLAVORS_INSTAGRAM_CLIENT_ID} (the Meta app id),
 * {@code FLAVORS_INSTAGRAM_CLIENT_SECRET}, {@code FLAVORS_INSTAGRAM_CONFIG_ID}, {@code FLAVORS_INSTAGRAM_REDIRECT_URI}
 * (plus the token encryption key, which is not used here). Without them {@link #isConfigured()} is false and
 * nothing is called.
 *
 * <p>Graph URLs carry the access token, so no URL and no Meta error text is ever put in an exception message or a log.
 */
@Service
class InstagramClientImpl implements InstagramClient {

    private static final String LOGIN_URL = "https://www.facebook.com/dialog/oauth";
    private static final String DEFAULT_GRAPH_URL = "https://graph.facebook.com/v27.0";

    /** How many recent posts a pull reads; the engagement rate is their average. */
    static final int RECENT_POSTS = 12;

    /** The Graph API's error code for a revoked, expired or otherwise invalid token. */
    private static final Pattern INVALID_TOKEN = Pattern.compile("\"code\"\\s*:\\s*190\\b");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> AS_MAP = new TypeReference<>() {
    };

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String configId;
    private final String redirectUri;
    private final String graphUrl;

    @Autowired
    InstagramClientImpl(@Value("${flavors.instagram.client-id:}") String clientId,
                        @Value("${flavors.instagram.client-secret:}") String clientSecret,
                        @Value("${flavors.instagram.config-id:}") String configId,
                        @Value("${flavors.instagram.redirect-uri:}") String redirectUri) {
        this(clientId, clientSecret, configId, redirectUri, DEFAULT_GRAPH_URL);
    }

    /** The graph address can be replaced so a test can point the client at a local stand-in server. */
    InstagramClientImpl(String clientId, String clientSecret, String configId, String redirectUri, String graphUrl) {
        this.graphUrl = graphUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.configId = configId;
        this.redirectUri = redirectUri;

        // Without timeouts a slow Meta would keep a request (and a database connection) waiting forever.
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !configId.isBlank() && !redirectUri.isBlank();
    }

    @Override
    public String authorizationUrl(String state) {
        // No scope here: the permissions are the ones of the configuration (config_id) made in the Meta dashboard.
        return UriComponentsBuilder.fromUriString(LOGIN_URL)
                .queryParam("client_id", clientId)
                .queryParam("config_id", configId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public String exchangeCode(String code) {
        // One step: the configuration issues a system-user token that doesn't expire. Server side only: it uses the secret.
        // redirect_uri must be the one the login dialog was opened with: Facebook-style logins refuse the code otherwise.
        Map<String, Object> token = call("code exchange", () -> http.get()
                .uri(graphUrl + "/oauth/access_token?client_id={id}&client_secret={secret}&code={code}"
                                + "&redirect_uri={redirect}",
                        clientId, clientSecret, code, redirectUri)
                .retrieve()
                .body(String.class));
        return text(token, "access_token");
    }

    @Override
    public List<AccountRef> assignedAccounts(String accessToken) {
        // /me is the system user, not the creator's account; the accounts she shared are listed here.
        Map<String, Object> accounts = call("account list", () -> http.get()
                .uri(graphUrl + "/me/assigned_instagram_accounts?fields=id,username&access_token={token}", accessToken)
                .retrieve()
                .body(String.class));
        List<AccountRef> result = new ArrayList<>();
        for (Map<String, Object> account : maps(accounts.get("data"))) {
            String username = account.get("username") == null ? null : String.valueOf(account.get("username"));
            result.add(new AccountRef(text(account, "id"), username));
        }
        return List.copyOf(result);
    }

    @Override
    public Snapshot fetchSnapshot(String accessToken, String externalId) {
        Map<String, Object> profile = call("profile read", () -> http.get()
                .uri(graphUrl + "/{id}?fields=username,followers_count&access_token={token}", externalId, accessToken)
                .retrieve()
                .body(String.class));
        String username = profile.get("username") == null ? null : String.valueOf(profile.get("username"));
        long followers = number(profile.get("followers_count"));

        Map<String, Object> media = call("post list", () -> http.get()
                .uri(graphUrl + "/{id}/media?fields=id,like_count,comments_count&limit={limit}&access_token={token}",
                        externalId, RECENT_POSTS, accessToken)
                .retrieve()
                .body(String.class));
        List<PostFigures> posts = new ArrayList<>();
        for (Map<String, Object> post : maps(media.get("data"))) {
            posts.add(figuresOf(post, accessToken));
        }
        return new Snapshot(externalId, username, followers, List.copyOf(posts));
    }

    /**
     * Reads views, likes, comments and shares from the post's insights. Some posts have no insights (albums,
     * very new posts), so then the like and comment counts from the post list are used, with 0 views and shares.
     * A revoked token is not hidden by that fallback.
     */
    private PostFigures figuresOf(Map<String, Object> post, String accessToken) {
        String postId = text(post, "id");
        long likes = number(post.get("like_count"));
        long comments = number(post.get("comments_count"));
        long views = 0;
        long shares = 0;
        try {
            Map<String, Object> insights = call("post insights", () -> http.get()
                    .uri(graphUrl + "/{post}/insights?metric=views,likes,comments,shares&access_token={token}",
                            postId, accessToken)
                    .retrieve()
                    .body(String.class));
            for (Map<String, Object> metric : maps(insights.get("data"))) {
                long value = firstValue(metric);
                switch (String.valueOf(metric.get("name"))) {
                    case "views" -> views = value;
                    case "likes" -> likes = value;
                    case "comments" -> comments = value;
                    case "shares" -> shares = value;
                    default -> { }
                }
            }
        } catch (SocialTokenInvalidException e) {
            throw e;
        } catch (SocialPlatformException e) {
            // No insights for this post: keep the counts from the post list.
        }
        return new PostFigures(postId, views, likes, comments, shares);
    }

    /**
     * Runs one request and turns every failure into our own exceptions, without the URL or Meta's text. The answer is
     * read as text and parsed here, not by Spring's converters: graph.facebook.com sends its JSON as
     * {@code text/javascript}, which Spring's JSON converter refuses.
     */
    private static Map<String, Object> call(String what, Supplier<String> request) {
        try {
            String text = request.get();
            if (text == null || text.isBlank()) {
                throw new SocialPlatformException("Meta sent an empty answer to the " + what + ".");
            }
            return MAPPER.readValue(text, AS_MAP);
        } catch (RestClientResponseException e) {
            String answer = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 401 || INVALID_TOKEN.matcher(answer).find()) {
                throw new SocialTokenInvalidException();
            }
            // Only the numbers and the error type of Meta's answer go into the message (enough to look the error up),
            // never its free text or the URL: those can carry details that don't belong in a log.
            throw new SocialPlatformException("Meta answered the " + what + " with status " + e.getStatusCode().value()
                    + errorDetails(answer) + ".");
        } catch (JacksonException e) {
            throw new SocialPlatformException("Meta's answer to the " + what + " was not readable JSON.");
        } catch (RestClientException e) {
            // The exception's type says what happened (timeout, refused connection, unreadable answer); its text could
            // hold the URL, so only the type is kept.
            throw new SocialPlatformException(
                    "Meta could not be reached for the " + what + " (" + e.getClass().getSimpleName() + ").");
        }
    }

    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_SUBCODE = Pattern.compile("\"error_subcode\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_TYPE = Pattern.compile("\"type\"\\s*:\\s*\"(\\w+)\"");

    /** " (OAuthException, code 100, subcode 33)" from a Graph error body; empty when it has none of them. */
    private static String errorDetails(String body) {
        StringBuilder details = new StringBuilder();
        Matcher type = ERROR_TYPE.matcher(body);
        if (type.find()) {
            details.append(type.group(1));
        }
        Matcher code = ERROR_CODE.matcher(body);
        if (code.find()) {
            details.append(details.isEmpty() ? "" : ", ").append("code ").append(code.group(1));
        }
        Matcher subcode = ERROR_SUBCODE.matcher(body);
        if (subcode.find()) {
            details.append(details.isEmpty() ? "" : ", ").append("subcode ").append(subcode.group(1));
        }
        return details.isEmpty() ? "" : " (" + details + ")";
    }

    /** An insight's value: {"values": [{"value": 123}]}, or {"total_value": {"value": 123}}. */
    private static long firstValue(Map<String, Object> metric) {
        List<Map<String, Object>> values = maps(metric.get("values"));
        if (!values.isEmpty()) {
            return number(values.get(0).get("value"));
        }
        if (metric.get("total_value") instanceof Map<?, ?> total) {
            return number(total.get("value"));
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static String text(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new SocialPlatformException("Meta's answer had no " + field + ".");
        }
        return String.valueOf(value);
    }

    /** A count from Meta's JSON (a number, or sometimes a string); missing or negative counts as 0. */
    private static long number(Object value) {
        long n;
        if (value instanceof Number number) {
            n = number.longValue();
        } else if (value instanceof String s && !s.isBlank()) {
            try {
                n = Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                n = 0;
            }
        } else {
            n = 0;
        }
        return Math.max(n, 0);
    }
}
