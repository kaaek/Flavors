package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CreatorSearchCriteria;
import com.flavors.dto.CreatorSearchFacets;
import com.flavors.dto.CreatorSearchResult;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.SearchPage;
import com.flavors.enums.Role;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.service.CreatorSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CreatorSearchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CreatorSearchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreatorSearchService service;

    CurrentUser brand = new CurrentUser(10L, "brand@example.com", Role.BRAND, "password", true);
    CurrentUser creator = new CurrentUser(5L, "creator@example.com", Role.CREATOR, "password", true);
    CurrentUser admin = new CurrentUser(1L, "admin@example.com", Role.ADMIN, "password", true);

    @Test
    void brandGetsResultsWithTheContractFieldNames() throws Exception {
        when(service.search(eq(10L), any())).thenReturn(new SearchPage<>(
                List.of(new CreatorSearchResult(5, "Mira", "Burgers", "Beirut", null, null, null,
                        1000, 5000, null, null, "mira", true)), 0, 20, 1));

        mvc.perform(get("/api/brand/creators/search").param("q", "burgers beirut").with(user(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].creatorId").value(5))
                .andExpect(jsonPath("$.items[0].displayName").value("Mira"))
                .andExpect(jsonPath("$.items[0].followers").isEmpty())
                .andExpect(jsonPath("$.items[0].roi").isEmpty())
                .andExpect(jsonPath("$.items[0].saved").value(true))
                .andExpect(jsonPath("$.items[0].storefrontSlug").value("mira"));
    }

    @Test
    void queryParametersReachTheServiceAndTheBrandIdComesFromTheSession() throws Exception {
        when(service.search(eq(10L), any())).thenReturn(new SearchPage<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/brand/creators/search")
                        .param("category", "Burgers").param("minPriceCents", "1000")
                        .param("maxPriceCents", "5000").param("minFollowers", "100")
                        .with(user(brand)))
                .andExpect(status().isOk());

        ArgumentCaptor<CreatorSearchCriteria> captor = ArgumentCaptor.forClass(CreatorSearchCriteria.class);
        verify(service).search(eq(10L), captor.capture());
        CreatorSearchCriteria criteria = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(criteria.category()).isEqualTo("Burgers");
        org.assertj.core.api.Assertions.assertThat(criteria.minPriceCents()).isEqualTo(1000L);
        org.assertj.core.api.Assertions.assertThat(criteria.maxPriceCents()).isEqualTo(5000L);
        org.assertj.core.api.Assertions.assertThat(criteria.minFollowers()).isEqualTo(100L);
        org.assertj.core.api.Assertions.assertThat(criteria.page()).isZero();
        org.assertj.core.api.Assertions.assertThat(criteria.size()).isEqualTo(20);
    }

    @Test
    void invertedPriceRangeIs400() throws Exception {
        mvc.perform(get("/api/brand/creators/search")
                        .param("minPriceCents", "9000").param("maxPriceCents", "1000").with(user(brand)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void invertedFollowerRangeAndOversizedPageAre400() throws Exception {
        mvc.perform(get("/api/brand/creators/search")
                        .param("minFollowers", "500").param("maxFollowers", "10").with(user(brand)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/brand/creators/search").param("size", "51").with(user(brand)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/brand/creators/search").param("page", "-1").with(user(brand)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousIs401AndOtherRolesAre403() throws Exception {
        mvc.perform(get("/api/brand/creators/search")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/brand/creators/search").with(user(creator))).andExpect(status().isForbidden());
        mvc.perform(get("/api/brand/creators/search").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/brand/creators/facets").with(user(creator))).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void facetsListLocationsAndCategories() throws Exception {
        when(service.facets()).thenReturn(new CreatorSearchFacets(List.of("Beirut"), List.of("Burgers")));

        mvc.perform(get("/api/brand/creators/facets").with(user(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations[0]").value("Beirut"))
                .andExpect(jsonPath("$.categories[0]").value("Burgers"));
    }
}
