package com.flavors.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.dto.CampaignResponse;
import com.flavors.dto.BranchResponse;
import com.flavors.config.SecurityConfig;
import com.flavors.dto.CreatorCampaignSummaryResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.Role;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.service.CampaignService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CreatorCampaignController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CreatorCampaignControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CampaignService campaigns;

    @Test
    void aCreatorCanSeeTheirCampaigns() throws Exception {
        when(campaigns.listForCreator(7L)).thenReturn(List.of(
                new CreatorCampaignSummaryResponse(
                        42L,
                        "Summer Campaign",
                        CampaignStatus.COUNTERED,
                        9L,
                        "Example Brand",
                        50000L,
                        60000L,
                        null,
                        null,
                        null)));

        mvc.perform(get("/api/creator/campaigns")
                        .with(user(new CurrentUser(7, "lara@example.com", Role.CREATOR, "x", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].name").value("Summer Campaign"))
                .andExpect(jsonPath("$[0].status").value("COUNTERED"))
                .andExpect(jsonPath("$[0].brandName").value("Example Brand"));

        verify(campaigns).listForCreator(7L);
    }

    @Test
    void aBrandCannotSeeCreatorCampaigns() throws Exception {
        mvc.perform(get("/api/creator/campaigns")
                        .with(user(new CurrentUser(8, "b@example.com", Role.BRAND, "x", true))))
                .andExpect(status().isForbidden());
    }
    @Test
void aCreatorCanViewTheirCampaignDetails() throws Exception {
    when(campaigns.getForCreator(7L, 42L)).thenReturn(
            new CampaignResponse(
                    42L,
                    "Summer Campaign",
                    CampaignStatus.COUNTERED,
                    new CampaignResponse.CreatorView(7L, "Lara"),
                    List.of(new BranchResponse(1L, "Main Branch", "Beirut")),
                    50000L,
                    null,
                    null,
                    List.of(),
                    "Instagram post",
                    null,
                    null,
                    60000L,
                    null,
                    null,
                    null,
                    null));

    mvc.perform(get("/api/creator/campaigns/42")
                    .with(user(new CurrentUser(7, "lara@example.com", Role.CREATOR, "x", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.name").value("Summer Campaign"))
            .andExpect(jsonPath("$.agreedPriceCents").value(60000));

    verify(campaigns).getForCreator(7L, 42L);
}

@Test
void aBrandCannotViewCreatorCampaignDetails() throws Exception {
    mvc.perform(get("/api/creator/campaigns/42")
                    .with(user(new CurrentUser(8, "b@example.com", Role.BRAND, "x", true))))
            .andExpect(status().isForbidden());
}
}
