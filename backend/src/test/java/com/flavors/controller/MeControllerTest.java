package com.flavors.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.mapper.MeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
// @WebMvcTest loads only the web layer, so the mapper the controller needs is imported by hand.
@Import({SecurityConfig.class, MeMapper.class})
class MeControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsTheLoggedInUser() throws Exception {
        CurrentUser lara = new CurrentUser(7, "lara@example.com", Role.CREATOR, "{bcrypt}hash", true);

        mvc.perform(get("/api/me").with(user(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("lara@example.com"))
                .andExpect(jsonPath("$.role").value("CREATOR"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void notLoggedInGets401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
