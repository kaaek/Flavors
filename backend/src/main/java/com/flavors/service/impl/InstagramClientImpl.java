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
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to the Instagram API with Instagram Login, as researched in SPIKE-1
 * ({@code docs/EEC-28-instagram-api-research.md}). It needs three settings from the Meta developer app:
 * {@code FLAVORS_INSTAGRAM_CLIENT_ID}, {@code FLAVORS_INSTAGRAM_CLIENT_SECRET} and
 * {@code FLAVORS_INSTAGRAM_REDIRECT_URI}. Without them {@link #isConfigured()} is false and nothing is called.
 *
 * <p>Instagram URLs carry the access token, so no URL and no Instagram error text is ever put in an
 * exception message or a log.
 */
@Service
class InstagramClientImpl implements InstagramClient {

    private static final String AUTHORIZE_URL = "https://www.instagram.com/oauth/authorize";
    private static final String TOKEN_URL = "https://api.instagram.com/oauth/access_token";
    private static final String DEFAULT_GRAPH_URL = "https://graph.instagram.com";

    /** SPIKE-1, section 4: basic profile data, and insights for views, likes, comments and shares. */
    private static final String SCOPES = "instagram_business_basic,instagram_business_manage_insights";

    /** How many recent posts a pull reads; the engagement rate is their average. */
    static final int RECENT_POSTS = 12;

    /** Instagram's error code for a revoked, expired or otherwise invalid token. */
    private static final Pattern INVALID_TOKEN = Pattern.compile("\"code\"\\s*:\\s*190\\b");

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String graphUrl;

    @Autowired
    InstagramClientImpl(@Value("${flavors.instagram.client-id:}") String clientId,
                        @Value("${flavors.instagram.client-secret:}") String clientSecret,
                        @Value("${flavors.instagram.redirect-uri:}") String redirectUri) {
        this(clientId, clientSecret, redirectUri, DEFAULT_GRAPH_URL);
    }

    /** The graph address can be replaced so a test can point the client at a local stand-in server. */
    InstagramClientImpl(String clientId, String clientSecret, String redirectUri, String graphUrl) {
        this.graphUrl = graphUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;

        // Without timeouts a slow Instagram would keep a request (and a database connection) waiting forever.
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    @Override
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("enable_fb_login", "0")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public String exchangeCode(String code) {
        // 1. The one-time code becomes a short-lived token (valid 1 hour).
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        Map<String, Object> shortLived = call(() -> http.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JSON));
        String shortToken = text(unwrapData(shortLived), "access_token");

        // 2. The short-lived token becomes a long-lived one (valid 60 days). Server side only: it uses the secret.
        Map<String, Object> longLived = call(() -> http.get()
                .uri(graphUrl + "/access_token?grant_type=ig_exchange_token&client_secret={secret}&access_token={token}",
                        clientSecret, shortToken)
                .retrieve()
                .body(JSON));
        return text(longLived, "access_token");
    }

    @Override
    public String renewToken(String accessToken) {
        // A long-lived token can be exchanged for a new 60-day one once it is 24 hours old. Server side only.
        Map<String, Object> renewed = call(() -> http.get()
                .uri(graphUrl + "/refresh_access_token?grant_type=ig_refresh_token&access_token={token}", accessToken)
                .retrieve()
                .body(JSON));
        return text(renewed, "access_token");
    }

    @Override
    public Snapshot fetchSnapshot(String accessToken) {
        Map<String, Object> me = call(() -> http.get()
                .uri(graphUrl + "/me?fields=user_id,username,followers_count&access_token={token}", accessToken)
                .retrieve()
                .body(JSON));
        // user_id is the professional account's id; id is only valid for this app. Prefer the first.
        String externalId = me.get("user_id") != null ? String.valueOf(me.get("user_id")) : text(me, "id");
        String username = me.get("username") == null ? null : String.valueOf(me.get("username"));
        long followers = number(me.get("followers_count"));

        Map<String, Object> media = call(() -> http.get()
                .uri(graphUrl + "/me/media?fields=id,like_count,comments_count&limit={limit}&access_token={token}",
                        RECENT_POSTS, accessToken)
                .retrieve()
                .body(JSON));
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
            Map<String, Object> insights = call(() -> http.get()
                    .uri(graphUrl + "/{post}/insights?metric=views,likes,comments,shares&access_token={token}",
                            postId, accessToken)
                    .retrieve()
                    .body(JSON));
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

    /** Runs one request and turns every failure into our own exceptions, without the URL or Instagram's text. */
    private static Map<String, Object> call(Supplier<Map<String, Object>> request) {
        try {
            Map<String, Object> body = request.get();
            if (body == null) {
                throw new SocialPlatformException("Instagram sent an empty answer.");
            }
            return body;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || INVALID_TOKEN.matcher(e.getResponseBodyAsString()).find()) {
                throw new SocialTokenInvalidException();
            }
            throw new SocialPlatformException("Instagram answered with status " + e.getStatusCode().value() + ".");
        } catch (RestClientException e) {
            throw new SocialPlatformException("Instagram could not be reached.");
        }
    }

    /** The code exchange wraps its answer in {"data": [ ... ]}; the other calls don't. */
    private static Map<String, Object> unwrapData(Map<String, Object> body) {
        List<Map<String, Object>> data = maps(body.get("data"));
        return data.isEmpty() ? body : data.get(0);
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
            throw new SocialPlatformException("Instagram's answer had no " + field + ".");
        }
        return String.valueOf(value);
    }

    /** A count from Instagram's JSON (a number, or sometimes a string); missing or negative counts as 0. */
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
