
package com.flavors.controller;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.entity.Review;
import com.flavors.enums.Role;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.exception.ReviewNotAllowedException;
import com.flavors.service.CampaignReviewService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignReviewController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CampaignReviewControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CampaignReviewService service;

    private final CurrentUser brand = new CurrentUser(
            1L, "brand@example.com", Role.BRAND, null, true);

    private final CurrentUser creator = new CurrentUser(
            2L, "creator@example.com", Role.CREATOR, null, true);

    private Review sampleReview() {
        return new Review(
                10L, (short) 4, "Great work",
                Instant.parse("2026-11-18T12:00:00Z"));
    }

    @Test
    void brandCanSubmitReview() throws Exception {
        when(service.submit(1L, 10L, (short) 4, "Great work"))
                .thenReturn(sampleReview());

        mvc.perform(post("/api/brand/campaigns/10/review")
                        .with(user(brand))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":4,"text":"Great work"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.text").value("Great work"))
                .andExpect(jsonPath("$.campaignId").value(10));

        verify(service).submit(1L, 10L, (short) 4, "Great work");
    }

    @Test
    void brandCanRetrieveReview() throws Exception {
        when(service.get(1L, 10L)).thenReturn(sampleReview());

        mvc.perform(get("/api/brand/campaigns/10/review")
                        .with(user(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    void noReviewReturns204() throws Exception {
        mvc.perform(get("/api/brand/campaigns/10/review")
                        .with(user(brand)))
                .andExpect(status().isNoContent());
    }

    @Test
    void anotherBrandsCampaignReturns404() throws Exception {
        when(service.get(1L, 10L))
                .thenThrow(new CampaignNotFoundException());

        mvc.perform(get("/api/brand/campaigns/10/review")
                        .with(user(brand)))
                .andExpect(status().isNotFound());
    }

    @Test
    void activeCampaignReturns409() throws Exception {
        when(service.submit(1L, 10L, (short) 4, "Good"))
                .thenThrow(new ReviewNotAllowedException(
                        "Campaign is not completed"));

        mvc.perform(post("/api/brand/campaigns/10/review")
                        .with(user(brand))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":4,"text":"Good"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateReviewReturns409() throws Exception {
        when(service.submit(1L, 10L, (short) 5, "Excellent"))
                .thenThrow(new ReviewNotAllowedException(
                        "Campaign already reviewed"));

        mvc.perform(post("/api/brand/campaigns/10/review")
                        .with(user(brand))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":5,"text":"Excellent"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void missingRatingReturns400() throws Exception {
        mvc.perform(post("/api/brand/campaigns/10/review")
                        .with(user(brand))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Good"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void creatorCannotSubmitReview() throws Exception {
        mvc.perform(post("/api/brand/campaigns/10/review")
                        .with(user(creator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":4,"text":"Good"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedUserCannotSubmitReview() throws Exception {
        mvc.perform(post("/api/brand/campaigns/10/review")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":4,"text":"Good"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
