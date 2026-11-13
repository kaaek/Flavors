package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.service.CampaignResponseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web side of responding to a campaign request (UC-F4.1): URLs, who may call them and the status codes.
 * The rules themselves are in CampaignResponseServiceImplTest; the service is a mock here.
 */
@WebMvcTest(CampaignResponseController.class)
@Import(SecurityConfig.class)
class CampaignResponseControllerTest {

    private static final CurrentUser LARA = new CurrentUser(7, "lara@example.com", Role.CREATOR, "{bcrypt}hash", true);
    private static final CurrentUser KARIM = new CurrentUser(9, "karim@example.com", Role.BRAND, "{bcrypt}hash", true);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CampaignResponseService service;

    @Test
    void aCreatorCanAccept() throws Exception {
        mvc.perform(post("/api/creator/campaigns/42/accept").with(user(LARA)).with(csrf()))
                .andExpect(status().isNoContent());

        then(service).should().acceptCampaign(7L, 42L);
    }

    @Test
    void aCreatorCanReject() throws Exception {
        mvc.perform(post("/api/creator/campaigns/42/reject").with(user(LARA)).with(csrf()))
                .andExpect(status().isNoContent());

        then(service).should().rejectCampaign(7L, 42L);
    }

    @Test
    void aCreatorCanCounterOffer() throws Exception {
        mvc.perform(post("/api/creator/campaigns/42/counter-offer")
                        .with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceCents\": 60000}"))
                .andExpect(status().isNoContent());

        then(service).should().submitCounterOffer(eq(7L), eq(42L), any());
    }

    @Test
    void aNegativeCounterPriceIsRefused() throws Exception {
        mvc.perform(post("/api/creator/campaigns/42/counter-offer")
                        .with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceCents\": -5}"))
                .andExpect(status().isBadRequest());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void aBrandCanApproveTheCounterOffer() throws Exception {
        mvc.perform(post("/api/brand/campaigns/42/counter-offer/approve").with(user(KARIM)).with(csrf()))
                .andExpect(status().isNoContent());

        then(service).should().approveCounterOffer(9L, 42L);
    }

    @Test
    void aBrandCanDeclineTheCounterOffer() throws Exception {
        mvc.perform(post("/api/brand/campaigns/42/counter-offer/decline").with(user(KARIM)).with(csrf()))
                .andExpect(status().isNoContent());

        then(service).should().declineCounterOffer(9L, 42L);
    }

    @Test
    void aBrandCannotAnswerOnBehalfOfACreator() throws Exception {
        mvc.perform(post("/api/creator/campaigns/42/accept").with(user(KARIM)).with(csrf()))
                .andExpect(status().isForbidden());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void aCreatorCannotApproveHerOwnCounterOffer() throws Exception {
        mvc.perform(post("/api/brand/campaigns/42/counter-offer/approve").with(user(LARA)).with(csrf()))
                .andExpect(status().isForbidden());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void anonymousIsNotLoggedIn() throws Exception {
        mvc.perform(post("/api/creator/campaigns/42/accept").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}