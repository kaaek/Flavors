package com.flavors.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CreatorPortfolioResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.service.PortfolioViewService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web side of the brand's portfolio view: the URL, who may call it, the JSON shape and the error body.
 * The rules are tested in PortfolioViewServiceImplIT; the service is a mock here.
 */
@WebMvcTest(CreatorPortfolioController.class)
@Import(SecurityConfig.class)
class CreatorPortfolioControllerTest {

    private static final CurrentUser KARIM = new CurrentUser(9, "karim@example.com", Role.BRAND, "{bcrypt}hash", true);
    private static final CurrentUser LARA = new CurrentUser(7, "lara@example.com", Role.CREATOR, "{bcrypt}hash", true);

    private static final CreatorPortfolioResponse PORTFOLIO = new CreatorPortfolioResponse(
            7, "Lara", "I love burgers", "Burgers", "Beirut", 2500, 10050,
            List.of(new CreatorPortfolioResponse.AccountView("instagram", 12000L, new BigDecimal("4.25"), null)),
            List.of(new CreatorPortfolioResponse.FeaturedCampaignView(42, "Summer burgers", true, 2, new BigDecimal("130.8"))),
            new BigDecimal("4.5"), 2,
            List.of(new CreatorPortfolioResponse.ReviewView(5, "Great reach", null)));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PortfolioViewService service;

    @Test
    void aBrandSeesTheVerifiedPortfolio() throws Exception {
        given(service.view(7L)).willReturn(PORTFOLIO);

        mvc.perform(get("/api/brand/creators/7/portfolio").with(user(KARIM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(7))
                .andExpect(jsonPath("$.displayName").value("Lara"))
                .andExpect(jsonPath("$.priceMinCents").value(2500))
                .andExpect(jsonPath("$.socialAccounts[0].platform").value("instagram"))
                .andExpect(jsonPath("$.socialAccounts[0].followers").value(12000))
                .andExpect(jsonPath("$.socialAccounts[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.featuredCampaigns[0].verified").value(true))
                .andExpect(jsonPath("$.featuredCampaigns[0].redemptions").value(2))
                .andExpect(jsonPath("$.featuredCampaigns[0].roiPercent").value(130.8))
                // The bills and discounts behind the ROI belong to the brand that ran the campaign.
                .andExpect(jsonPath("$.featuredCampaigns[0].revenueCents").doesNotExist())
                .andExpect(jsonPath("$.featuredCampaigns[0].discountCents").doesNotExist())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.reviews[0].rating").value(5));
    }

    @Test
    void anUnknownOrUnapprovedCreatorGets404() throws Exception {
        given(service.view(8L)).willThrow(new CreatorNotFoundException(8L));

        mvc.perform(get("/api/brand/creators/8/portfolio").with(user(KARIM)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Creator 8 was not found."));
    }

    @Test
    void notLoggedInGets401() throws Exception {
        mvc.perform(get("/api/brand/creators/7/portfolio")).andExpect(status().isUnauthorized());
    }

    @Test
    void aCreatorGets403() throws Exception {
        mvc.perform(get("/api/brand/creators/7/portfolio").with(user(LARA))).andExpect(status().isForbidden());
    }

    @Test
    void anIdThatIsNotANumberGets400() throws Exception {
        mvc.perform(get("/api/brand/creators/abc/portfolio").with(user(KARIM))).andExpect(status().isBadRequest());
    }
}
