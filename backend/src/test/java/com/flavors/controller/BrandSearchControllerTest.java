package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.BrandSearchResult;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.SearchPage;
import com.flavors.enums.Role;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.service.BrandSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BrandSearchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BrandSearchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrandSearchService service;

    CurrentUser brand = new CurrentUser(10L, "brand@example.com", Role.BRAND, "password", true);
    CurrentUser creator = new CurrentUser(5L, "creator@example.com", Role.CREATOR, "password", true);

    @Test
    void creatorGetsResultsWithoutContactDetails() throws Exception {
        when(service.search(any())).thenReturn(new SearchPage<>(
                List.of(new BrandSearchResult(10, "Karim's Kitchen", "Burgers", "Smash burgers",
                        List.of("Beirut", "Jounieh"))), 0, 20, 1));

        mvc.perform(get("/api/creator/brands/search").param("q", "burgers").with(user(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].brandId").value(10))
                .andExpect(jsonPath("$.items[0].businessName").value("Karim's Kitchen"))
                .andExpect(jsonPath("$.items[0].cuisine").value("Burgers"))
                .andExpect(jsonPath("$.items[0].description").value("Smash burgers"))
                .andExpect(jsonPath("$.items[0].branchLocations[1]").value("Jounieh"))
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.items[0].contactPerson").doesNotExist());
    }

    @Test
    void oversizedPageIs400() throws Exception {
        mvc.perform(get("/api/creator/brands/search").param("size", "500").with(user(creator)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void anonymousIs401AndBrandIs403() throws Exception {
        mvc.perform(get("/api/creator/brands/search")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/creator/brands/search").with(user(brand))).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
