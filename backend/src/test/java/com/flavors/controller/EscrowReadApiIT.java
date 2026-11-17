package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PayoutStatus;
import com.flavors.enums.PaymentTransactionType;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.Role;
import com.flavors.entity.PaymentTransaction;
import com.flavors.repository.EscrowReleaseOperationRepository;
import com.flavors.repository.PaymentTransactionRepository;
import com.flavors.service.EscrowPayoutService;
import com.flavors.service.EscrowReleaseService;
import com.flavors.service.ProofReviewService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"flavors.payout.mode=simulated", "flavors.payout.processing-cron=-",
        "flavors.escrow.release-deadline-cron=-"})
@AutoConfigureMockMvc
class EscrowReadApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProofReviewService reviews;
    @Autowired EscrowReleaseService releases;
    @Autowired EscrowPayoutService payouts;
    @Autowired EscrowReleaseOperationRepository operations;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired TransactionTemplate tx;

    @ParameterizedTest
    @EnumSource(PayoutStatus.class)
    void owningBrandAndCreatorReadTheSameSnapshotAndAccurateProgress(PayoutStatus status) throws Exception {
        Fixture f = fixture();
        reviews.review(f.brand(), f.campaign(), f.submission(), new ProofReviewRequest(ProofReviewDecision.APPROVED, null));
        long id = operations.findByEscrowId(f.escrow()).orElseThrow().getId();
        if (status == PayoutStatus.CONFIRMED) {
            assertThat(payouts.processOperation(id)).isTrue();
        } else if (status != PayoutStatus.AUTHORIZED) {
            tx.executeWithoutResult(s -> {
                var op = operations.findById(id).orElseThrow();
                var payment = transactions.saveAndFlush(new PaymentTransaction(f.escrow(), PaymentTransactionType.RELEASE,
                        op.getCreatorNetCents(), null, op.getOperationKey()));
                Instant now = Instant.now();
                op.beginDispatch("SIMULATED", "private-destination", payment.getId(), UUID.randomUUID(), now.plusSeconds(120), now);
                operations.flush(); // Persist UNKNOWN before a subsequent provider-outcome transition, as production does.
                if (status == PayoutStatus.SUBMITTED) op.recordSubmitted("private-reference", now);
                if (status == PayoutStatus.UNKNOWN) op.recordUnknown("PRIVATE_ERROR", now.plusSeconds(60), now);
                if (status == PayoutStatus.FAILED) op.recordFailed("PRIVATE_ERROR", false, null, now);
            });
        }
        for (Role role : new Role[]{Role.BRAND, Role.CREATOR}) {
            var response = mvc.perform(get(path(f, role)).with(user(principal(f, role))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.campaignId").value(f.campaign()))
                    .andExpect(jsonPath("$.amountCents").value(20003))
                    .andExpect(jsonPath("$.feeCents").value(3000))
                    .andExpect(jsonPath("$.creatorNetCents").value(17003))
                    .andExpect(jsonPath("$.payoutStatus").value(status.name()))
                    .andExpect(jsonPath("$.status").value(status == PayoutStatus.CONFIRMED ? "RELEASED" : "HELD"))
                    .andExpect(jsonPath("$.authorizationSource").value("APPROVAL"))
                    .andExpect(jsonPath("$.simulated").value(true))
                    .andExpect(jsonPath("$.authorizedAt").isString())
                    .andExpect(jsonPath("$.payoutAvailable").value(true))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("private-destination", "private-reference", "PRIVATE_ERROR",
                    "operationKey", "providerRef", "payoutProvider", "destinationRef", "leaseToken", "lastErrorCode",
                    "paymentTransactionId", "dispatchAttempts", "secret", "credential");
        }
    }

    @ParameterizedTest
    @EnumSource(EscrowStatus.class)
    void existingEscrowStatesWithoutAnOperationKeepNullablePayoutDetails(EscrowStatus state) throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE escrow_payment SET status = ? WHERE id = ?", state.name(), f.escrow());
        mvc.perform(get(path(f, Role.BRAND)).with(user(principal(f, Role.BRAND))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state.name()))
                .andExpect(jsonPath("$.amountCents").value(20003))
                .andExpect(jsonPath("$.payoutStatus").isEmpty()).andExpect(jsonPath("$.feeCents").isEmpty())
                .andExpect(jsonPath("$.creatorNetCents").isEmpty()).andExpect(jsonPath("$.simulated").isEmpty());
    }

    @Test
    void timerCAuthorizationAndFrozenRejectionAreVisibleWithoutAPayoutClaim() throws Exception {
        Fixture timer = fixture();
        jdbc.update("UPDATE campaign SET proof_review_deadline = now() - interval '1 second' WHERE id = ?", timer.campaign());
        assertThat(releases.authorizeOverdue(timer.campaign())).isTrue();
        mvc.perform(get(path(timer, Role.CREATOR)).with(user(principal(timer, Role.CREATOR))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorizationSource").value("TIMER_C"))
                .andExpect(jsonPath("$.payoutStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$.status").value("HELD")).andExpect(jsonPath("$.confirmedAt").isEmpty());
        Fixture rejected = fixture();
        reviews.review(rejected.brand(), rejected.campaign(), rejected.submission(),
                new ProofReviewRequest(ProofReviewDecision.REJECTED, "Missing"));
        mvc.perform(get(path(rejected, Role.CREATOR)).with(user(principal(rejected, Role.CREATOR))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.payoutStatus").isEmpty());
    }

    @Test
    void ownershipRolesAnonymousAndDraftVisibilityRemainProtected() throws Exception {
        Fixture f = fixture();
        mvc.perform(get(path(f, Role.BRAND)).with(user(new CurrentUser(f.brand() + 10000, "other@example.com", Role.BRAND, "x", true))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(f, Role.CREATOR)).with(user(new CurrentUser(f.creator() + 10000, "other@example.com", Role.CREATOR, "x", true))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(f, Role.BRAND)).with(user(principal(f, Role.CREATOR)))).andExpect(status().isForbidden());
        mvc.perform(get(path(f, Role.CREATOR)).with(user(principal(f, Role.BRAND)))).andExpect(status().isForbidden());
        mvc.perform(get(path(f, Role.BRAND))).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE campaign SET status = 'DRAFT' WHERE id = ?", f.campaign());
        mvc.perform(get(path(f, Role.CREATOR)).with(user(principal(f, Role.CREATOR)))).andExpect(status().isNotFound());
    }

    private String path(Fixture f, Role role) { return "/api/" + role.name().toLowerCase() + "/campaigns/" + f.campaign() + "/escrow"; }
    private CurrentUser principal(Fixture f, Role role) {
        return new CurrentUser(role == Role.BRAND ? f.brand() : f.creator(), "owner@example.com", role, "x", true);
    }
    private Fixture fixture() {
        long brand = userId("BRAND"); long creator = userId("CREATOR");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'K', '1')", brand);
        jdbc.update("INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents) "
                + "VALUES (?, 'Creator', 'Food', 'Beirut', 0, 0)", creator);
        long campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                    start_date, last_posting_date, proof_review_deadline)
                VALUES (?, ?, 'Escrow API', 'PROOF_SUBMITTED', 20003, 20003, current_date, current_date,
                    now() + interval '5 days') RETURNING id
                """, Long.class, brand, creator);
        long escrow = jdbc.queryForObject("INSERT INTO escrow_payment (campaign_id, amount_cents, provider, status, held_at) "
                + "VALUES (?, 20003, 'collection', 'HELD', now()) RETURNING id", Long.class, campaign);
        long submission = jdbc.queryForObject("INSERT INTO proof_submission (campaign_id, round, status, submitted_at) "
                + "VALUES (?, 1, 'SUBMITTED', now()) RETURNING id", Long.class, campaign);
        return new Fixture(brand, creator, campaign, escrow, submission);
    }
    private long userId(String role) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com", role);
    }
    private record Fixture(long brand, long creator, long campaign, long escrow, long submission) {}
}
