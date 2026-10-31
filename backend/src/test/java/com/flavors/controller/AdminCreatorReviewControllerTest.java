package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CreatorReviewResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.PendingCreatorResponse;
import com.flavors.enums.Role;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.exception.ReviewNotAllowedException;
import com.flavors.service.CreatorReviewService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AdminCreatorReviewController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminCreatorReviewControllerTest {

    static final CurrentUser ADMIN = new CurrentUser(1, "admin@example.com", Role.ADMIN, "x", true);
    static final CurrentUser CREATOR = new CurrentUser(7, "lara@example.com", Role.CREATOR, "x", true);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreatorReviewService reviews;

    @Test
    void adminSeesTheQueue() throws Exception {
        when(reviews.listPending()).thenReturn(List.of(
                new PendingCreatorResponse(7, "Lara", "Street food", "Beirut", Instant.parse("2026-10-31T10:00:00Z"))));

        mvc.perform(get("/api/admin/creators/pending").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].displayName").value("Lara"));
    }

    @Test // TC-F20.1.1
    void adminApprovesAndTheDecisionIsAttributedToThem() throws Exception {
        when(reviews.approve(1L, 7L)).thenReturn(review(VerificationStatus.APPROVED, null));

        mvc.perform(withCsrf(post("/api/admin/creators/7/approve")).with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));

        verify(reviews).approve(1L, 7L);
    }

    @Test // TC-F20.1.2
    void rejectingWithoutAReasonIsRefused() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/creators/7/reject"))
                        .with(user(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("A reason is required to reject a profile."));

        verifyNoInteractions(reviews);
    }

    @Test // TC-F20.1.2
    void rejectingWithAReasonWorks() throws Exception {
        when(reviews.reject(eq(1L), eq(7L), any()))
                .thenReturn(review(VerificationStatus.REJECTED, "Account is private."));

        mvc.perform(withCsrf(post("/api/admin/creators/7/reject"))
                        .with(user(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Account is private.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Account is private."));
    }

    @Test // TC-F20.1.3
    void approvalIsBlockedWhenTheSocialAccountNeedsReconnection() throws Exception {
        doThrow(new ReviewNotAllowedException("Needs reconnection: approval is blocked."))
                .when(reviews).approve(1L, 7L);

        mvc.perform(withCsrf(post("/api/admin/creators/7/approve")).with(user(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Needs reconnection: approval is blocked."));
    }

    @Test
    void aCreatorCannotUseTheAdminArea() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/creators/7/approve")).with(user(CREATOR)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviews);
    }

    @Test
    void notLoggedInGets401() throws Exception {
        mvc.perform(get("/api/admin/creators/pending")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "token")).header("X-XSRF-TOKEN", "token");
    }

    private static CreatorReviewResponse review(VerificationStatus status, String reason) {
        return new CreatorReviewResponse(7, "lara@example.com", "Lara", null, "Street food", "Beirut", 10000, 30000,
                status, reason, false, Instant.parse("2026-10-31T10:00:00Z"), null, List.of());
    }
}
