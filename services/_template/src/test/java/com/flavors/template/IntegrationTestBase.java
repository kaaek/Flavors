package com.flavors.template;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the whole service against real Postgres and RabbitMQ (Testcontainers) and a tiny JWKS server
 * that plays identity, so tests sign their own tokens. The containers and the key are shared by all test
 * classes in the JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "flavors.platform.outbox.poll-interval=200ms",
        "flavors.platform.security.issuer=http://identity.test"})
abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    static final RSAKey SIGNING_KEY;
    static final RSAKey OTHER_KEY;
    static final HttpServer JWKS_SERVER;

    static {
        try {
            POSTGRES.start();
            RABBIT.start();
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            OTHER_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate(); // same kid, other key
            byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            JWKS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS_SERVER.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            JWKS_SERVER.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("flavors.platform.security.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/.well-known/jwks.json");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    // No automatic cookie handling and no redirects: tests set every header themselves.
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void cleanTables() {
        // audit_log is append-only (triggers), so it is never cleaned: tests look for their own rows.
        jdbc.update("DELETE FROM idempotency_key");
    }

    static String token(long id, String role, Boolean mfa) {
        return token(SIGNING_KEY, id, role, mfa, Instant.now().plusSeconds(300), builder -> { });
    }

    static String token(RSAKey key, long id, String role, Boolean mfa, Instant expiresAt,
                        Consumer<JWTClaimsSet.Builder> customize) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(id))
                    .issuer("http://identity.test")
                    .issueTime(Date.from(Instant.now().minusSeconds(1)))
                    .expirationTime(Date.from(expiresAt));
            if (role != null) {
                claims.claim("role", role);
            }
            if (mfa != null) {
                claims.claim("mfa", mfa);
            }
            customize.accept(claims);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    record Response(int status, String body, java.net.http.HttpHeaders headers) {
        boolean contains(String text) {
            return body.contains(text);
        }

        List<String> setCookies() {
            return headers.allValues("set-cookie");
        }
    }

    Response send(String method, String path, Map<String, String> headers, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            headers.forEach(request::header);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Response get(String path, String bearer) {
        return send("GET", path, bearer == null ? Map.of() : Map.of("Authorization", "Bearer " + bearer), null);
    }

    Response postJson(String path, Map<String, String> headers, String json) {
        var all = new java.util.HashMap<>(headers);
        all.put("Content-Type", "application/json");
        return send("POST", path, all, json);
    }
}
