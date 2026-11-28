package com.flavors;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.support.BrandFixtures;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole service over real HTTP: JWTs signed by a test "identity" (a JWKS server in this JVM) against the
 * monolith's schema on real Postgres. It proves what the gateway will rely on: the endpoints answer with the
 * platform's role rules, and the cross-read of {@code creator_profile} works.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandServiceIT {

    static final RSAKey SIGNING_KEY;
    static final HttpServer JWKS_SERVER;

    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("brand-test-key").generate();
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
        registry.add("flavors.platform.security.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/.well-known/jwks.json");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void profileNeedsABrandToken() {
        long brandId = BrandFixtures.brand(jdbc, "Karim's Kitchen", "Burgers", "Smash");

        assertThat(call("GET", "/api/brand/profile", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/api/brand/profile", token(brandId, "CREATOR"), null).status()).isEqualTo(403);
        Response ok = call("GET", "/api/brand/profile", token(brandId, "BRAND"), null);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body()).contains("\"businessName\":\"Karim's Kitchen\"").contains("\"cuisine\":\"Burgers\"");
    }

    @Test
    void brandAddsAndListsItsBranches() {
        long brandId = BrandFixtures.brand(jdbc, "Branchy", null, null);
        String token = token(brandId, "BRAND");

        Response added = call("POST", "/api/brand/branches", token, "{\"name\":\"Jounieh\",\"location\":\"Keserwan\"}");
        Response list = call("GET", "/api/brand/branches", token, null);

        assertThat(added.status()).isEqualTo(201);
        assertThat(list.body()).contains("Hamra").contains("Jounieh");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'BRANCH_ADDED' AND actor_user_id = ?",
                Integer.class, brandId)).isEqualTo(1);
    }

    @Test
    void creatorsSearchBrandsButBrandsCannot() {
        String word = "zq" + UUID.randomUUID().toString().replace("-", "").replaceAll("[0-9]", "").substring(0, 8) + "xk";
        long brandId = BrandFixtures.brand(jdbc, "Search " + word, "Burgers", null);

        Response found = call("GET", "/api/creator/brands/search?q=" + word, token(99, "CREATOR"), null);

        assertThat(found.status()).isEqualTo(200);
        assertThat(found.body()).contains("\"brandId\":" + brandId).doesNotContain("contactPerson");
        assertThat(call("GET", "/api/creator/brands/search", token(brandId, "BRAND"), null).status()).isEqualTo(403);
    }

    @Test
    void savedCreatorsUseTheCreatorProfileCrossRead() {
        long brandId = BrandFixtures.brand(jdbc, "Saver", null, null);
        long approved = creator("APPROVED");
        long pending = creator("PENDING_APPROVAL");
        String token = token(brandId, "BRAND");

        assertThat(call("POST", "/api/brand/saved-creators/" + approved, token, null).status()).isEqualTo(204);
        // Saving twice is harmless (ON CONFLICT DO NOTHING).
        assertThat(call("POST", "/api/brand/saved-creators/" + approved, token, null).status()).isEqualTo(204);
        assertThat(call("POST", "/api/brand/saved-creators/" + pending, token, null).status()).isEqualTo(409);
        assertThat(call("POST", "/api/brand/saved-creators/987654321", token, null).status()).isEqualTo(404);

        Response list = call("GET", "/api/brand/saved-creators", token, null);
        assertThat(list.body()).contains("\"creatorId\":" + approved).doesNotContain("\"creatorId\":" + pending);

        assertThat(call("DELETE", "/api/brand/saved-creators/" + approved, token, null).status()).isEqualTo(204);
        assertThat(call("GET", "/api/brand/saved-creators", token, null).body()).isEqualTo("[]");
    }

    private long creator(String status) {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'CREATOR', 'ACTIVE')
                RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents,
                                             price_max_cents, verification_status, rejection_reason)
                VALUES (?, 'Creator', 'Food', 'Beirut', 1000, 5000, ?, NULL)
                """, id, status);
        return id;
    }

    private static String token(long id, String role) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(String.valueOf(id)).claim("role", role)
                    .issueTime(Date.from(Instant.now().minusSeconds(1)))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    record Response(int status, String body) {
    }

    // A Bearer header (service-to-service style) is not CSRF-checked; the cookie double-submit is the platform's.
    private Response call(String method, String path, String bearer, String json) {
        try {
            Map<String, String> headers = new HashMap<>();
            if (bearer != null) {
                headers.put("Authorization", "Bearer " + bearer);
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            headers.forEach(request::header);
            if (json != null) {
                request.header("Content-Type", "application/json");
            }
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
