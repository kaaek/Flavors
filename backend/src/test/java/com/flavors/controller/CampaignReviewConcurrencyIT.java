
package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import java.util.List;
import org.springframework.test.annotation.DirtiesContext;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CampaignReviewConcurrencyIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void simultaneousReviewRequestsCreateOnlyOneReview() throws Exception {
        long brandId = newUser("BRAND");
        long creatorId = newUser("CREATOR");

        jdbc.update("""
                INSERT INTO brand
                    (user_id, business_name, contact_person, phone)
                VALUES (?, 'Test Brand', 'Tester', '12345678')
                """, brandId);

        jdbc.update("""
                INSERT INTO creator_profile
                    (user_id, display_name, food_category, location,
                     price_min_cents, price_max_cents)
                VALUES (?, 'Test Creator', 'Food', 'Beirut', 0, 0)
                """, creatorId);

        long campaignId = jdbc.queryForObject("""
                INSERT INTO campaign
                    (brand_id, creator_id, name, status,
                     price_offered_cents, agreed_price_cents,
                     start_date, last_posting_date)
                VALUES (?, ?, 'Completed Campaign', 'COMPLETED',
                        10000, 10000, current_date, current_date)
                RETURNING id
                """, Long.class, brandId, creatorId);

        CurrentUser brand = new CurrentUser(
                brandId, "brand@example.com", Role.BRAND, null, true);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> submit = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }

            return mvc.perform(post(
                            "/api/brand/campaigns/{id}/review", campaignId)
                            .with(user(brand))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating":5,"text":"Great work"}
                                    """))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(submit);
            var second = executor.submit(submit);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int firstStatus = first.get(20, TimeUnit.SECONDS);
            int secondStatus = second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(201, 409);
        }

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM review WHERE campaign_id = ?",
                Integer.class, campaignId);

        assertThat(count).isEqualTo(1);
    }

    private long newUser(String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role)
                VALUES (?, 'x', ?)
                RETURNING id
                """, Long.class,
                UUID.randomUUID() + "@example.com", role);
    }
}
