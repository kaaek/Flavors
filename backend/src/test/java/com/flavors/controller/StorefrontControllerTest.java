package com.flavors.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.StorefrontResponse;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.service.impl.StorefrontService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StorefrontController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class StorefrontControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StorefrontService service;

    @Test
    void anonymousVisitorCanReadPublishedStorefront() throws Exception {
        when(service.publicStorefront("lara-7")).thenReturn(page());
        mvc.perform(get("/api/public/storefronts/lara-7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Lara"))
                .andExpect(jsonPath("$.offers").isEmpty());
    }

    @Test
    void privateCreatorEndpointRequiresCreatorRole() throws Exception {
        mvc.perform(get("/api/creator/storefront")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/creator/storefront").with(user("brand").roles("BRAND")))
                .andExpect(status().isForbidden());
    }

    private static StorefrontResponse page() {
        return new StorefrontResponse("Lara", null, "Food", "Beirut", "lara-7", List.of());
    }
}
