package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.OfferRequest;
import com.flavors.dto.OfferResponse;
import com.flavors.enums.OfferState;
import com.flavors.enums.Role;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.exception.OfferCreatorRequiredException;
import com.flavors.exception.OfferLockedException;
import com.flavors.exception.OfferValidationException;
import com.flavors.service.OfferService;
import jakarta.servlet.http.Cookie;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The web side of the follower offer (EEC-41): who may call it, CSRF, the JSON both ways and the error bodies. The rules
 * themselves are tested in OfferServiceImplIT; the service is a mock here.
 */
@WebMvcTest(BrandOfferController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BrandOfferControllerTest {

    private static final String URL = "/api/brand/campaigns/42/offer";

    private static final String BODY = """
            {"discountPercent":20,"expiresOn":"2030-02-01","validDays":["MONDAY","FRIDAY"],
             "validFrom":"12:00","validTo":"15:30","allBranches":false,"branchIds":[11,12],
             "minPurchaseCents":2000,"maxRedemptions":100,"perPersonLimit":2}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OfferService offers;

    @Test
    void brandSavesTheOfferAndGetsItBack() throws Exception {
        when(offers.save(eq(7L), eq(42L), any())).thenReturn(response());

        mvc.perform(json(put(URL), BODY).with(user(brand())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.campaignId").value(42))
                .andExpect(jsonPath("$.creatorId").value(3))
                .andExpect(jsonPath("$.discountPercent").value(20))
                .andExpect(jsonPath("$.expiresOn").value("2030-02-01"))
                .andExpect(jsonPath("$.expiresAt").value("2030-02-01T22:00:00Z"))
                .andExpect(jsonPath("$.validDays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.validFrom").value("12:00"))
                .andExpect(jsonPath("$.branchIds[1]").value(12))
                .andExpect(jsonPath("$.state").value("NOT_LIVE_YET"))
                .andExpect(jsonPath("$.editable").value(true));

        // The JSON field names reach the service as they should.
        verify(offers).save(7L, 42L, new OfferRequest(20, LocalDate.parse("2030-02-01"),
                List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "12:00", "15:30", false, List.of(11L, 12L), 2000L, 100, 2));
    }

    @Test
    void getReturnsTheOfferOr204WhenThereIsNone() throws Exception {
        when(offers.get(7L, 42L)).thenReturn(Optional.of(response()));
        when(offers.get(7L, 43L)).thenReturn(Optional.empty());

        mvc.perform(get(URL).with(user(brand())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountPercent").value(20));
        mvc.perform(get("/api/brand/campaigns/43/offer").with(user(brand())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void wrongFieldsGet400WithEveryFieldNamed() throws Exception {
        when(offers.save(eq(7L), eq(42L), any())).thenThrow(new OfferValidationException(Map.of(
                "discountPercent", "Enter a discount from 1 to 100%.",
                "expiresOn", "The expiry date can't be in the past.")));

        mvc.perform(json(put(URL), BODY.replace("\"discountPercent\":20", "\"discountPercent\":150"))
                        .with(user(brand())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid offer"))
                .andExpect(jsonPath("$.errors.discountPercent").value("Enter a discount from 1 to 100%."))
                .andExpect(jsonPath("$.errors.expiresOn").value("The expiry date can't be in the past."));
    }

    // EX-F10.1.3. Bound to Integer, the JSON reader would have cut 100.9 down to 100 before any check ran.
    @Test
    void aFractionalDiscountReachesTheChecksUncutAndIsRefused() throws Exception {
        when(offers.save(eq(7L), eq(42L), any())).thenThrow(new OfferValidationException(Map.of(
                "discountPercent", "Enter a whole number, like 20.")));

        mvc.perform(json(put(URL), """
                        {"discountPercent":100.9,"expiresOn":"2030-02-01"}
                        """).with(user(brand())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.discountPercent").value("Enter a whole number, like 20."));

        ArgumentCaptor<OfferRequest> sent = ArgumentCaptor.forClass(OfferRequest.class);
        verify(offers).save(eq(7L), eq(42L), sent.capture());
        assertThat(sent.getValue().discountPercent()).isEqualByComparingTo("100.9");
    }

    // FR-F10.1: a draft without a creator can't get an offer yet; the page shows the brand what to do.
    @Test
    void aCampaignWithoutACreatorAnswersCreatorRequired() throws Exception {
        when(offers.save(eq(7L), eq(42L), any())).thenThrow(new OfferCreatorRequiredException());

        mvc.perform(json(put(URL), BODY).with(user(brand())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Creator required"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.startsWith("Choose the campaign's creator")));
    }

    @Test
    void anActiveCampaignsOfferIsLocked() throws Exception {
        when(offers.save(eq(7L), eq(42L), any())).thenThrow(new OfferLockedException());

        mvc.perform(json(put(URL), BODY).with(user(brand())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Offer locked"));
    }

    @Test
    void anotherBrandsCampaignIsNotFound() throws Exception {
        when(offers.get(anyLong(), anyLong())).thenThrow(new CampaignNotFoundException());
        when(offers.save(anyLong(), anyLong(), any())).thenThrow(new CampaignNotFoundException());

        mvc.perform(get(URL).with(user(brand()))).andExpect(status().isNotFound());
        mvc.perform(json(put(URL), BODY).with(user(brand()))).andExpect(status().isNotFound());
    }

    @Test
    void onlyBrandsGetInAndChangesNeedTheCsrfToken() throws Exception {
        CurrentUser creator = new CurrentUser(9, "nour@example.com", Role.CREATOR, "x", true);

        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(user(creator))).andExpect(status().isForbidden());
        mvc.perform(json(put(URL), BODY).with(user(creator))).andExpect(status().isForbidden());
        // A brand without the CSRF cookie and header is refused.
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(BODY).with(user(brand())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(offers);
    }

    private static CurrentUser brand() {
        return new CurrentUser(7, "karim@example.com", Role.BRAND, "x", true);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body)
                .cookie(new Cookie("XSRF-TOKEN", "token")).header("X-XSRF-TOKEN", "token");
    }

    private static OfferResponse response() {
        return new OfferResponse(5, 42, 3L, 20, LocalDate.parse("2030-02-01"), Instant.parse("2030-02-01T22:00:00Z"),
                List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "12:00", "15:30", false, List.of(11L, 12L), 2000, 100, 2,
                0, OfferState.NOT_LIVE_YET, true, Instant.parse("2030-01-10T10:00:00Z"));
    }
}
