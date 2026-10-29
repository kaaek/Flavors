package com.flavors.common.web;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Uses the placeholder SPA in src/test/resources/static. In production the Dockerfile puts the real
 * build in the same place.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SpaForwardingIT {

    @Autowired
    MockMvc mvc;

    // "/" itself is Spring's welcome page, which forwards to /index.html (MockMvc doesn't follow
    // forwards); it is checked against the real container in the Docker smoke test.
    @Test
    void clientSideRoutesGetIndexHtml() throws Exception {
        for (String path : new String[] {"/index.html", "/brand/campaigns", "/s/some-creator", "/claim/abc123"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("spa-index")));
        }
    }

    @Test
    void realFilesAreServedAsThemselves() throws Exception {
        mvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("console.log")))
                .andExpect(content().string(not(containsString("spa-index"))));
    }

    @Test
    void missingFilesAndApiPathsStay404() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("spa-index"))));
    }

    @Test
    void apiRoutesStillReachSecurity() throws Exception {
        // Not logged in: SecurityConfig answers 401 as JSON, not the SPA.
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("spa-index"))));
    }
}
