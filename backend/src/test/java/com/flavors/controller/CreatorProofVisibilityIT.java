package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * A draft that already names the creator stays hidden from her on the proof and review endpoints, as on her campaign
 * pages (Architecture section 16, NF-1): each answers 404 exactly like an unknown campaign. CI runs this with
 * Testcontainers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CreatorProofVisibilityIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aDraftThatNamesTheCreatorAnswersLikeAnUnknownCampaignOnEveryProofEndpoint() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        long brandId = insertUser(UUID.randomUUID() + "@example.com", "BRAND");
        long creatorId = insertUser(email, "CREATOR");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'K', '1')",
                brandId);
        jdbc.update("""
                INSERT INTO creator_profile
                    (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Creator', 'Food', 'Beirut', 10000, 30000)
                """, creatorId);
        long draft = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, content_requirements)
                VALUES (?, ?, 'Unsent draft', 'DRAFT', 'Not for the creator yet')
                RETURNING id
                """, Long.class, brandId, creatorId);
        long unknown = jdbc.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM campaign", Long.class);
        CurrentUser creator = new CurrentUser(creatorId, email, Role.CREATOR, "{noop}x", true);

        for (String path : new String[] {"/proof", "/proof/reviews"}) {
            assertSameNotFound(get("/api/creator/campaigns/" + draft + path).with(user(creator)),
                    get("/api/creator/campaigns/" + unknown + path).with(user(creator)));
        }
        for (String path : new String[] {"/proof/draft", "/proof/submit"}) {
            HttpMethod method = path.endsWith("draft") ? HttpMethod.PUT : HttpMethod.POST;
            assertSameNotFound(proofForm(method, "/api/creator/campaigns/" + draft + path, creator),
                    proofForm(method, "/api/creator/campaigns/" + unknown + path, creator));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_submission WHERE campaign_id = ?", Integer.class,
                draft)).isZero();
    }

    /** Both answer 404 with the same body apart from the path, so the creator can't tell a draft from nothing. */
    private void assertSameNotFound(RequestBuilder draft, RequestBuilder unknown) throws Exception {
        String draftBody = mvc.perform(draft).andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();
        String unknownBody = mvc.perform(unknown).andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();
        assertThat(withoutInstance(draftBody)).isEqualTo(withoutInstance(unknownBody));
        assertThat(draftBody).doesNotContain("Unsent draft").doesNotContain("Not for the creator yet");
    }

    private static String withoutInstance(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\"", "");
    }

    /** The multipart draft or submit request the SPA sends, with its CSRF cookie and header. */
    private static RequestBuilder proofForm(HttpMethod method, String path, CurrentUser creator) {
        MockMultipartFile proof = new MockMultipartFile("proof", "", "application/json",
                "{\"links\":[\"https://example.com/post\"]}".getBytes());
        return multipart(method, path).file(proof).with(user(creator))
                .cookie(new Cookie("XSRF-TOKEN", "token")).header("X-XSRF-TOKEN", "token");
    }

    private long insertUser(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role) VALUES (?, '{noop}x', ?) RETURNING id
                """, Long.class, email, role);
    }
}
