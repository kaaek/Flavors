package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CampaignProofReviewsResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.ProofReviewResponse;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofReviewFollowUp;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.service.ProofReviewService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProofReviewController.class)
@Import(SecurityConfig.class)
class ProofReviewControllerTest {

    private static final CurrentUser BRAND = new CurrentUser(9, "brand@example.com", Role.BRAND, "x", true);
    private static final CurrentUser CREATOR = new CurrentUser(7, "creator@example.com", Role.CREATOR, "x", true);
    private static final CurrentUser ADMIN = new CurrentUser(11, "admin@example.com", Role.ADMIN, "x", true);
    private static final String REVIEW = "/api/brand/campaigns/42/proof/5/review";
    private static final ProofReviewResponse APPROVAL = new ProofReviewResponse(100, 5, ProofReviewDecision.APPROVED,
            null, Instant.parse("2026-11-07T08:00:00Z"), ProofReviewFollowUp.RELEASE_PAYMENT);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProofReviewService service;

    @Test
    void owningBrandCanRecordApprovalWithAnExplicitPendingFollowUp() throws Exception {
        given(service.review(eq(9L), eq(42L), eq(5L), any())).willReturn(APPROVAL);

        mvc.perform(post(REVIEW).with(user(BRAND)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").doesNotExist())
                .andExpect(jsonPath("$.requiredFollowUp").value("RELEASE_PAYMENT"))
                .andExpect(jsonPath("$.paymentReleased").doesNotExist());
    }

    @Test
    void historyIsReadThroughTheUsersOwnRoleEndpoint() throws Exception {
        var response = new CampaignProofReviewsResponse(42, CampaignStatus.PROOF_SUBMITTED, 0, null, 5L, null,
                List.of(), List.of(APPROVAL));
        given(service.history(9, Role.BRAND, 42)).willReturn(response);
        given(service.history(7, Role.CREATOR, 42)).willReturn(response);
        given(service.history(11, Role.ADMIN, 42)).willReturn(response);

        mvc.perform(get("/api/brand/campaigns/42/proof/reviews").with(user(BRAND)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviews[0].submissionId").value(5));
        mvc.perform(get("/api/creator/campaigns/42/proof/reviews").with(user(CREATOR)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviews[0].decidedBy").doesNotExist());
        mvc.perform(get("/api/admin/campaigns/42/proof/reviews").with(user(ADMIN)))
                .andExpect(status().isOk());
        verify(service).history(7, Role.CREATOR, 42);
    }

    @Test
    void wrongRoleCannotMakeABrandDecision() throws Exception {
        mvc.perform(post(REVIEW).with(user(CREATOR)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(REVIEW).with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/campaigns/42/proof/reviews").with(user(BRAND)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestsAreRefused() throws Exception {
        mvc.perform(get("/api/brand/campaigns/42/proof/reviews"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(REVIEW).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void aDecisionRequiresCsrf() throws Exception {
        mvc.perform(post(REVIEW).with(user(BRAND)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"decision\":null}", "{\"decision\":\"PAY_NOW\"}", "{"})
    void invalidDecisionBodiesNeverReachTheService(String body) throws Exception {
        mvc.perform(post(REVIEW).with(user(BRAND)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void oversizedReasonsAreRejectedAtTheWebBoundary() throws Exception {
        mvc.perform(post(REVIEW).with(user(BRAND)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        verifyNoInteractions(service);
    }

    @Test
    void ownershipFailuresAndConflictsUseTheExistingProblemDetailConvention() throws Exception {
        given(service.review(eq(9L), eq(42L), eq(5L), any()))
                .willThrow(new DeliveryProofNotFoundException())
                .willThrow(new DeliveryProofConflictException("Already reviewed."))
                .willThrow(new ValidationFailedException("reason", "A reason is required."));

        mvc.perform(post(REVIEW).with(user(BRAND)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(REVIEW).with(user(BRAND)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Already reviewed."));
        mvc.perform(post(REVIEW).with(user(BRAND)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    }
}
