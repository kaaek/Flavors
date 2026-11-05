package com.flavors.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIT {

    @Autowired
    MockMvc mvc;

    // No login: the docs must be readable by anyone, and must list real endpoints.
    @Test
    void apiDocsArePublicAndDescribeTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Flavors API")))
                .andExpect(content().string(containsString("/api/me")));
    }

    @Test
    void swaggerUiIsReachableWithoutLogin() throws Exception {
        // springdoc redirects /swagger-ui.html to its UI; a 401 here would mean SecurityConfig blocks it.
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
