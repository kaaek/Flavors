package com.flavors.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The prod profile (application-prod.yml) must switch the API docs off. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class OpenApiDisabledInProdIT {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsAndSwaggerUiAreNotServed() throws Exception {
        // /v3/api-docs has no file extension, so SpaForwardingConfig answers it with the React app's
        // index.html (200). What matters is that no OpenAPI document comes back.
        mvc.perform(get("/v3/api-docs")).andExpect(content().string(not(containsString("openapi"))));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
