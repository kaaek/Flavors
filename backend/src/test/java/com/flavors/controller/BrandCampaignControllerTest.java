package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.BranchResponse;
import com.flavors.dto.CampaignCreatorResponse;
import com.flavors.dto.CampaignRequest;
import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CampaignSummaryResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import com.flavors.enums.Role;
import com.flavors.exception.CampaignNotEditableException;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignValidationException;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.exception.NoBranchException;
import com.flavors.service.CampaignService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(BrandCampaignController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BrandCampaignControllerTest {

    private static final String BODY = """
            {"name":"Summer","creatorId":3,"branchIds":[11],"priceOfferedCents":25000,
             "startDate":"2030-01-10","lastPostingDate":"2030-01-20",
             "deliverables":[{"platform":"INSTAGRAM","type":"REEL","count":2}],"contentRequirements":"Be kind"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CampaignService campaigns;

    @Test
    void brandSavesADraftAndGetsItBack() throws Exception {
        when(campaigns.saveDraft(eq(7L), any())).thenReturn(response(CampaignStatus.DRAFT));

        mvc.perform(json(post("/api/brand/campaigns"), BODY).with(user(brand())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.creator.displayName").value("Nour"))
                .andExpect(jsonPath("$.priceOfferedCents").value(25000))
                .andExpect(jsonPath("$.deliverables[0].type").value("REEL"))
                .andExpect(jsonPath("$.branches[0].name").value("Hamra"));

        verify(campaigns).saveDraft(7L, new CampaignRequest("Summer", 3L, List.of(11L), 25000L,
                LocalDate.parse("2030-01-10"), LocalDate.parse("2030-01-20"),
                List.of(new CampaignRequest.DeliverableRequest("INSTAGRAM", "REEL", 2)), "Be kind"));
    }

    @Test
    void theBrandIsTheLoggedInUserNotAnIdFromTheRequest() throws Exception {
        when(campaigns.send(eq(7L), any())).thenReturn(response(CampaignStatus.AWAITING_CREATOR_RESPONSE));

        mvc.perform(json(post("/api/brand/campaigns/send"), BODY.replace("{\"name\"", "{\"brandId\":99,\"name\"")).with(user(brand())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_CREATOR_RESPONSE"))
                .andExpect(jsonPath("$.responseDeadline").value("2030-01-04T10:00:00Z"));
    }

    @Test
    void brandUpdatesAndSendsAnExistingDraft() throws Exception {
        when(campaigns.updateDraft(eq(7L), eq(42L), any())).thenReturn(response(CampaignStatus.DRAFT));
        when(campaigns.send(eq(7L), eq(42L), any())).thenReturn(response(CampaignStatus.AWAITING_CREATOR_RESPONSE));

        mvc.perform(json(put("/api/brand/campaigns/42"), BODY).with(user(brand()))).andExpect(status().isOk());
        mvc.perform(json(post("/api/brand/campaigns/42/send"), BODY).with(user(brand())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CREATOR_RESPONSE"));
    }

    @Test
    void brandListsAndOpensItsCampaignsAndLooksUpACreator() throws Exception {
        when(campaigns.list(7)).thenReturn(List.of(new CampaignSummaryResponse(42L, "Summer", CampaignStatus.DRAFT, null, null,
                null, Instant.parse("2030-01-01T10:00:00Z"))));
        when(campaigns.get(7, 42)).thenReturn(response(CampaignStatus.DRAFT));
        when(campaigns.findCreator(3)).thenReturn(new CampaignCreatorResponse(3L, "Nour", "Burgers", "Beirut"));

        mvc.perform(get("/api/brand/campaigns").with(user(brand())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Summer"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
        mvc.perform(get("/api/brand/campaigns/42").with(user(brand()))).andExpect(status().isOk());
        mvc.perform(get("/api/brand/campaigns/creators/3").with(user(brand())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nour"));
    }

    @Test
    void missingFieldsComeBackAsOneMessagePerField() throws Exception {
        when(campaigns.send(eq(7L), any())).thenThrow(new CampaignValidationException(
                Map.of("priceOfferedCents", "Enter the price you offer.", "deliverables[0].count", "Enter 1 or more.")));

        mvc.perform(json(post("/api/brand/campaigns/send"), "{\"name\":\"x\"}").with(user(brand())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid campaign"))
                .andExpect(jsonPath("$.errors.priceOfferedCents").value("Enter the price you offer."))
                .andExpect(jsonPath("$.errors['deliverables[0].count']").value("Enter 1 or more."));
    }

    @Test
    void domainRefusalsMapToTheirStatuses() throws Exception {
        when(campaigns.saveDraft(eq(7L), any())).thenThrow(new NoBranchException());
        when(campaigns.get(7, 5)).thenThrow(new CampaignNotFoundException());
        when(campaigns.updateDraft(eq(7L), eq(6L), any())).thenThrow(new CampaignNotEditableException());
        when(campaigns.findCreator(8)).thenThrow(new CreatorNotFoundException(8));

        mvc.perform(json(post("/api/brand/campaigns"), BODY).with(user(brand())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("No branch"));
        mvc.perform(get("/api/brand/campaigns/5").with(user(brand()))).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/brand/campaigns/6"), BODY).with(user(brand())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Campaign already sent"));
        mvc.perform(get("/api/brand/campaigns/creators/8").with(user(brand()))).andExpect(status().isNotFound());
    }

    // EX-F3.1.2: a creator can't reach a brand's draft; the guard stops her before the service is called.
    @Test
    void aCreatorCannotUseTheBrandCampaignEndpoints() throws Exception {
        CurrentUser creator = currentUser(9, Role.CREATOR);

        mvc.perform(get("/api/brand/campaigns").with(user(creator))).andExpect(status().isForbidden());
        mvc.perform(get("/api/brand/campaigns/42").with(user(creator))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/brand/campaigns"), BODY).with(user(creator))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/brand/campaigns/42/send"), BODY).with(user(creator))).andExpect(status().isForbidden());

        verifyNoInteractions(campaigns);
    }

    @Test
    void anonymousIsRefusedAndWritesNeedTheCsrfToken() throws Exception {
        mvc.perform(get("/api/brand/campaigns")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/brand/campaigns").contentType(MediaType.APPLICATION_JSON).content(BODY).with(user(brand())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(campaigns);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body)
                .cookie(new Cookie("XSRF-TOKEN", "token")).header("X-XSRF-TOKEN", "token");
    }

    private static CampaignResponse response(CampaignStatus status) {
        boolean sent = status == CampaignStatus.AWAITING_CREATOR_RESPONSE;
        return new CampaignResponse(42L, "Summer", status, new CampaignResponse.CreatorView(3L, "Nour"),
                List.of(new BranchResponse(11L, "Hamra", "Beirut")), 25000L, LocalDate.parse("2030-01-10"),
                LocalDate.parse("2030-01-20"),
                List.of(new CampaignResponse.DeliverableView(DeliverablePlatform.INSTAGRAM, DeliverableType.REEL, 2)),
                "Be kind", sent ? Instant.parse("2030-01-01T10:00:00Z") : null,
                sent ? Instant.parse("2030-01-04T10:00:00Z") : null, Instant.parse("2030-01-01T09:00:00Z"),
                Instant.parse("2030-01-01T10:00:00Z"));
    }

    private static CurrentUser brand() {
        return currentUser(7, Role.BRAND);
    }

    private static CurrentUser currentUser(long id, Role role) {
        return new CurrentUser(id, role.name().toLowerCase() + "@example.com", role, null, true);
    }
}
