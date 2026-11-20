package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.Role;
import com.flavors.service.DeliveryProofService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-F6.6 through the whole HTTP stack (security filters, JSON, controller, service, PostgreSQL): the brand's decision
 * is recorded within five seconds of the request. Network time to a real client is not included. CI runs this with
 * Testcontainers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProofReviewApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    DeliveryProofService proofs;

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(value = ProofReviewDecision.class, names = {"APPROVED", "REJECTED"})
    void aDecisionIsRecordedWithinFiveSecondsOfTheRequest(ProofReviewDecision decision) {
        Fixture fixture = newFixture();
        String reason = decision == ProofReviewDecision.REJECTED ? ",\"reason\":\"The reel was never posted\"" : "";
        String body = "{\"decision\":\"" + decision.name() + "\"" + reason + "}";

        String review = "/api/brand/campaigns/" + fixture.campaignId() + "/proof/" + fixture.submissionId() + "/review";

        // The CSRF cookie and header the SPA sends. Not csrf(): it swaps the shared context's cookie token repository
        // for a session one, and SecurityIT, which runs in the same context, then finds no XSRF-TOKEN cookie.
        assertTimeout(Duration.ofSeconds(5), () -> mvc.perform(post(review)
                        .with(user(fixture.brand()))
                        .cookie(new Cookie("XSRF-TOKEN", "token")).header("X-XSRF-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value(decision.name())));

        assertThat(jdbc.queryForObject("SELECT decision FROM proof_review WHERE submission_id = ?", String.class,
                fixture.submissionId())).isEqualTo(decision.name());
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE campaign_id = ?", String.class,
                fixture.campaignId())).isEqualTo(decision == ProofReviewDecision.REJECTED ? "FROZEN" : "HELD");
        assertTimeout(Duration.ofSeconds(5), () -> mvc.perform(get("/api/brand/campaigns/{campaignId}/proof/reviews",
                        fixture.campaignId()).with(user(fixture.brand())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].decision").value(decision.name()))
                .andExpect(jsonPath("$.availableDecisions").isEmpty()));
    }

    private Fixture newFixture() {
        String email = UUID.randomUUID() + "@example.com";
        long brandId = insertUser(email, "BRAND");
        long creatorId = insertUser(UUID.randomUUID() + "@example.com", "CREATOR");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'K', '1')",
                brandId);
        jdbc.update("""
                INSERT INTO creator_profile
                    (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Creator', 'Food', 'Beirut', 10000, 30000)
                """, creatorId);
        long campaignId = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents,
                    agreed_price_cents, start_date, last_posting_date)
                VALUES (?, ?, 'API timing', 'ACTIVE', 20000, 20000, current_date - 2, current_date + 2)
                RETURNING id
                """, Long.class, brandId, creatorId);
        jdbc.update("""
                INSERT INTO escrow_payment (campaign_id, status, amount_cents, provider, held_at)
                VALUES (?, 'HELD', 20000, 'test-only', now())
                """, campaignId);
        long submissionId = proofs.submit(creatorId, campaignId,
                new ProofDraftRequest(List.of("https://example.com/post"), null), List.of())
                .submissions().getFirst().id();
        return new Fixture(new CurrentUser(brandId, email, Role.BRAND, "{noop}x", true), campaignId, submissionId);
    }

    private long insertUser(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role) VALUES (?, '{noop}x', ?) RETURNING id
                """, Long.class, email, role);
    }

    private record Fixture(CurrentUser brand, long campaignId, long submissionId) {
    }
}
