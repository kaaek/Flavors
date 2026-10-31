package com.flavors.controller;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.enums.Role;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.InvalidPortfolioException;
import com.flavors.exception.PortfolioNotAvailableException;
import com.flavors.service.PortfolioService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
 
/**
 * The web side of Manage Portfolio: URLs, who may call them, the JSON shape and the error bodies.
 * The rules themselves are tested in PortfolioServiceImplIT; the service is a mock here.
 * The exception handler is picked up by @WebMvcTest on its own.
 */
@WebMvcTest(PortfolioController.class)
@Import(SecurityConfig.class)
class PortfolioControllerTest {
 
    private static final CurrentUser LARA = new CurrentUser(7, "lara@example.com", Role.CREATOR, "{bcrypt}hash", true);
    private static final CurrentUser KARIM = new CurrentUser(9, "karim@example.com", Role.BRAND, "{bcrypt}hash", true);
 
    private static final String EDIT = """
            {"bio": "I love burgers", "foodCategory": "Desserts", "location": "Sidon",
             "priceMinCents": 2000, "priceMaxCents": 8000, "featuredCampaignIds": [42]}
            """;
 
    private static final PortfolioResponse PORTFOLIO = new PortfolioResponse(
            "Lara", "I love burgers", "Desserts", "Sidon", 2000, 8000,
            List.of(new PortfolioResponse.SocialAccountView("instagram", SocialAccountStatus.CONNECTED, 12000L, new BigDecimal("4.25"), null)),
            List.of(new PortfolioResponse.CampaignView(42, "Summer burgers", true)));
 
    @Autowired
    MockMvc mvc;
 
    @MockitoBean
    PortfolioService service;
 
    @Test
    void creatorSeesHerPortfolio() throws Exception {
        given(service.getMyPortfolio(7L)).willReturn(PORTFOLIO);
 
        mvc.perform(get("/api/creator/portfolio").with(user(LARA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Lara"))
                .andExpect(jsonPath("$.priceMinCents").value(2000))
                .andExpect(jsonPath("$.socialAccounts[0].platform").value("instagram"))
                .andExpect(jsonPath("$.socialAccounts[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$.socialAccounts[0].followers").value(12000))
                .andExpect(jsonPath("$.socialAccounts[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.completedCampaigns[0].id").value(42))
                .andExpect(jsonPath("$.completedCampaigns[0].featured").value(true));
    }
 
    @Test
    void creatorSavesHerEdit() throws Exception {
        given(service.updateMyPortfolio(eq(7L), any())).willReturn(PORTFOLIO);
 
        mvc.perform(put("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("I love burgers"));
 
        // The JSON field names reach the service correctly, and the id comes from the login, not the body.
        ArgumentCaptor<UpdatePortfolioRequest> sent = ArgumentCaptor.forClass(UpdatePortfolioRequest.class);
        then(service).should().updateMyPortfolio(eq(7L), sent.capture());
        assertThat(sent.getValue().foodCategory()).isEqualTo("Desserts");
        assertThat(sent.getValue().priceMinCents()).isEqualTo(2000L);
        assertThat(sent.getValue().priceMaxCents()).isEqualTo(8000L);
        assertThat(sent.getValue().featuredCampaignIds()).containsExactly(42L);
    }
 
    // EX-F1.2.1 / EX-F1.2.2 at HTTP level: a refused edit is a 400 with the message in the body.
    @Test
    void refusedEditGets400WithTheMessage() throws Exception {
        given(service.updateMyPortfolio(eq(7L), any()))
                .willThrow(new InvalidPortfolioException("Minimum price can't be above the maximum price."));
 
        mvc.perform(put("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid portfolio"))
                .andExpect(jsonPath("$.detail").value("Minimum price can't be above the maximum price."));
    }
 
    @Test
    void creatorWhoIsNotApprovedGets409() throws Exception {
        given(service.getMyPortfolio(7L)).willThrow(new PortfolioNotAvailableException());
 
        mvc.perform(get("/api/creator/portfolio").with(user(LARA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Portfolio not available"));
    }
 
    @Test
    void notLoggedInGets401() throws Exception {
        mvc.perform(get("/api/creator/portfolio")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/creator/portfolio").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isUnauthorized());
    }
 
    @Test
    void aBrandGets403() throws Exception {
        mvc.perform(get("/api/creator/portfolio").with(user(KARIM))).andExpect(status().isForbidden());
        mvc.perform(put("/api/creator/portfolio").with(user(KARIM)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isForbidden());
    }
}