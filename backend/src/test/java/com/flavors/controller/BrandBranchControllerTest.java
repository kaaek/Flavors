package com.flavors.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.BranchResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.exception.ApiExceptionHandler;
import com.flavors.service.BrandBranchService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BrandBranchController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class BrandBranchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrandBranchService branches;

    @Test
    void brandCanListItsBranches() throws Exception {
        CurrentUser brand = currentUser(7, Role.BRAND);
        when(branches.list(7)).thenReturn(List.of(new BranchResponse(11L, "Hamra", "Beirut")));

        mvc.perform(get("/api/brand/branches").with(user(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hamra"));
    }

    @Test
    void brandCanAddABranchToItsOwnAccount() throws Exception {
        CurrentUser brand = currentUser(7, Role.BRAND);
        when(branches.add(7, new com.flavors.dto.BranchRegistrationRequest("Jounieh", "Keserwan")))
                .thenReturn(new BranchResponse(12L, "Jounieh", "Keserwan"));

        mvc.perform(post("/api/brand/branches")
                        .with(user(brand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jounieh","location":"Keserwan"}
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12));

        verify(branches).add(7, new com.flavors.dto.BranchRegistrationRequest("Jounieh", "Keserwan"));
    }

    @Test
    void creatorCannotUseBrandBranchEndpoints() throws Exception {
        mvc.perform(get("/api/brand/branches").with(user(currentUser(9, Role.CREATOR))))
                .andExpect(status().isForbidden());
    }

    private static CurrentUser currentUser(long id, Role role) {
        return new CurrentUser(id, role.name().toLowerCase() + "@example.com", role, null, true);
    }
}
