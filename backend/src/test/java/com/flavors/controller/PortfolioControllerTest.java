package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.enums.Role;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.AlreadyFeaturedException;
import com.flavors.exception.FeatureNotFoundException;
import com.flavors.exception.InvalidPortfolioException;
import com.flavors.exception.PortfolioNotAvailableException;
import com.flavors.service.PortfolioService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
             "priceMinCents": 2000, "priceMaxCents": 8000}
            """;

    private static final PortfolioResponse PORTFOLIO = new PortfolioResponse(
            "Lara", "I love burgers", "Desserts", "Sidon", 2000, 8000,
            List.of(new PortfolioResponse.SocialAccountView("instagram", SocialAccountStatus.CONNECTED, 12000L, new BigDecimal("4.25"), null)),
            List.of(new PortfolioResponse.CampaignView(42, "Summer burgers", true)));

    private static final PortfolioResponse.CampaignView FEATURED = new PortfolioResponse.CampaignView(42, "Summer burgers", true);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PortfolioService service;

    // ---- GET ----

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
    void creatorWhoIsNotApprovedGets409() throws Exception {
        given(service.getMyPortfolio(7L)).willThrow(new PortfolioNotAvailableException());

        mvc.perform(get("/api/creator/portfolio").with(user(LARA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Portfolio not available"));
    }

    // ---- PUT ----

    @Test
    void putReplacesTheDetails() throws Exception {
        given(service.updateMyPortfolio(eq(7L), any())).willReturn(PORTFOLIO);

        mvc.perform(put("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("I love burgers"));

        // The JSON field names reach the service correctly, and the id comes from the login, not the body.
        ArgumentCaptor<UpdatePortfolioRequest> sent = ArgumentCaptor.forClass(UpdatePortfolioRequest.class);
        then(service).should().updateMyPortfolio(eq(7L), sent.capture());
        assertThat(sent.getValue().bio()).isEqualTo("I love burgers");
        assertThat(sent.getValue().foodCategory()).isEqualTo("Desserts");
        assertThat(sent.getValue().location()).isEqualTo("Sidon");
        assertThat(sent.getValue().priceMinCents()).isEqualTo(2000L);
        assertThat(sent.getValue().priceMaxCents()).isEqualTo(8000L);
    }

    // The old body had a featuredCampaignIds list. It is ignored now: PUT cannot change the featured list.
    @Test
    void putIgnoresAFeaturedCampaignIdsFieldInTheBody() throws Exception {
        given(service.updateMyPortfolio(eq(7L), any())).willReturn(PORTFOLIO);
        String oldBody = """
                {"bio": "I love burgers", "foodCategory": "Desserts", "location": "Sidon",
                 "priceMinCents": 2000, "priceMaxCents": 8000, "featuredCampaignIds": []}
                """;

        mvc.perform(put("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(oldBody))
                .andExpect(status().isOk());

        then(service).should().updateMyPortfolio(eq(7L), any());
        then(service).shouldHaveNoMoreInteractions();
    }

    // EX-F1.2.2 at HTTP level: a refused edit is a 400 with the message in the body.
    @Test
    void refusedPutGets400WithTheMessage() throws Exception {
        given(service.updateMyPortfolio(eq(7L), any()))
                .willThrow(new InvalidPortfolioException("Minimum price can't be above the maximum price."));

        mvc.perform(put("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid portfolio"))
                .andExpect(jsonPath("$.detail").value("Minimum price can't be above the maximum price."));
    }

    // ---- PATCH ----

    @Test
    void patchSendsOnlyTheFieldsThatWereSent() throws Exception {
        given(service.patchMyPortfolio(eq(7L), anyMap())).willReturn(PORTFOLIO);

        mvc.perform(patch("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": \"New bio\"}"))
                .andExpect(status().isOk());

        then(service).should().patchMyPortfolio(eq(7L),
                argThat(changes -> changes.size() == 1 && "New bio".equals(changes.get("bio"))));
    }

    @Test
    void patchTellsAFieldSentAsNullApartFromAFieldLeftOut() throws Exception {
        given(service.patchMyPortfolio(eq(7L), anyMap())).willReturn(PORTFOLIO);

        mvc.perform(patch("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": null}"))
                .andExpect(status().isOk());

        then(service).should().patchMyPortfolio(eq(7L),
                argThat(changes -> changes.size() == 1 && changes.containsKey("bio") && changes.get("bio") == null));
    }

    @Test
    void refusedPatchGets400WithTheMessage() throws Exception {
        given(service.patchMyPortfolio(eq(7L), anyMap()))
                .willThrow(new InvalidPortfolioException("Unknown field: bioo."));

        mvc.perform(patch("/api/creator/portfolio").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bioo\": \"typo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field: bioo."));
    }

    // ---- POST /featured ----

    @Test
    void featuringACampaignGets201() throws Exception {
        given(service.featureCampaign(7L, 42L)).willReturn(FEATURED);

        mvc.perform(post("/api/creator/portfolio/featured").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"campaignId\": 42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.featured").value(true));
    }

    // EX-F1.2.1 at HTTP level.
    @Test
    void featuringACampaignThatIsNotCompletedGets400() throws Exception {
        given(service.featureCampaign(7L, 42L))
                .willThrow(new InvalidPortfolioException("Only your completed campaigns can be featured."));

        mvc.perform(post("/api/creator/portfolio/featured").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"campaignId\": 42}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only your completed campaigns can be featured."));
    }

    @Test
    void featuringAnAlreadyFeaturedCampaignGets409() throws Exception {
        given(service.featureCampaign(7L, 42L)).willThrow(new AlreadyFeaturedException());

        mvc.perform(post("/api/creator/portfolio/featured").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"campaignId\": 42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Already featured"));
    }

    // Two parallel requests: the second one hits the primary key. It is a 409, never a 500, and the
    // database message is not sent to the browser.
    @Test
    void aDatabaseConstraintViolationGets409WithoutTheDatabaseMessage() throws Exception {
        given(service.featureCampaign(7L, 42L))
                .willThrow(new DataIntegrityViolationException("duplicate key value violates portfolio_feature_pkey"));

        mvc.perform(post("/api/creator/portfolio/featured").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"campaignId\": 42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("portfolio_feature"))));
    }

    @Test
    void featuringWithoutACampaignIdReachesTheServiceAsNull() throws Exception {
        given(service.featureCampaign(7L, null)).willThrow(new InvalidPortfolioException("campaignId is required."));

        mvc.perform(post("/api/creator/portfolio/featured").with(user(LARA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("campaignId is required."));
    }

    // ---- DELETE /featured/{campaignId} ----

    @Test
    void unfeaturingGets204() throws Exception {
        mvc.perform(delete("/api/creator/portfolio/featured/42").with(user(LARA)).with(csrf()))
                .andExpect(status().isNoContent());

        then(service).should().unfeatureCampaign(7L, 42L);
    }

    @Test
    void unfeaturingACampaignThatIsNotFeaturedGets404() throws Exception {
        willThrow(new FeatureNotFoundException()).given(service).unfeatureCampaign(7L, 42L);

        mvc.perform(delete("/api/creator/portfolio/featured/42").with(user(LARA)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not featured"));
    }

    @Test
    void aCampaignIdThatIsNotANumberGets400() throws Exception {
        mvc.perform(delete("/api/creator/portfolio/featured/abc").with(user(LARA)).with(csrf()))
                .andExpect(status().isBadRequest());

        then(service).shouldHaveNoInteractions();
    }

    // ---- 401, 403 and CSRF on every verb ----

    private static MockHttpServletRequestBuilder call(String verb) {
        return switch (verb) {
            case "GET" -> get("/api/creator/portfolio");
            case "PUT" -> put("/api/creator/portfolio")
                    .contentType(MediaType.APPLICATION_JSON).content(EDIT);
            case "PATCH" -> patch("/api/creator/portfolio")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": \"x\"}");
            case "POST" -> post("/api/creator/portfolio/featured")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"campaignId\": 42}");
            case "DELETE" -> delete("/api/creator/portfolio/featured/42");
            default -> throw new IllegalArgumentException(verb);
        };
    }

    @ParameterizedTest(name = "{0} without a login gets 401")
    @ValueSource(strings = {"GET", "PUT", "PATCH", "POST", "DELETE"})
    void notLoggedInGets401(String verb) throws Exception {
        mvc.perform(call(verb).with(csrf())).andExpect(status().isUnauthorized());

        then(service).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "{0} as a brand gets 403")
    @ValueSource(strings = {"GET", "PUT", "PATCH", "POST", "DELETE"})
    void aBrandGets403(String verb) throws Exception {
        mvc.perform(call(verb).with(user(KARIM)).with(csrf())).andExpect(status().isForbidden());

        then(service).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "{0} without a CSRF token gets 403")
    @ValueSource(strings = {"PUT", "PATCH", "POST", "DELETE"})
    void aWriteWithoutACsrfTokenGets403(String verb) throws Exception {
        mvc.perform(call(verb).with(user(LARA))).andExpect(status().isForbidden());

        then(service).shouldHaveNoInteractions();
    }
}