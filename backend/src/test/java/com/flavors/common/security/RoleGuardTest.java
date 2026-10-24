package com.flavors.common.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Acceptance criterion 3: the role guard returns 403 for the wrong role. Runs only the web layer
 * (no database), with the real SecurityConfig and the test-only RoleProbeController.
 */
@WebMvcTest(RoleProbeController.class)
@Import(SecurityConfig.class)
class RoleGuardTest {

    @Autowired
    MockMvc mvc;

    /** Every role against every role area: allowed only in its own. */
    static Stream<Arguments> everyRoleInEveryArea() {
        return Arrays.stream(Role.values()).flatMap(role -> Stream.of("creator", "brand", "admin")
                .map(area -> Arguments.of(role, area, role.name().equalsIgnoreCase(area))));
    }

    @ParameterizedTest(name = "{0} on /api/{1}/** allowed: {2}")
    @MethodSource("everyRoleInEveryArea")
    void eachRoleReachesOnlyItsOwnArea(Role role, String area, boolean allowed) throws Exception {
        mvc.perform(get("/api/" + area + "/probe").with(user("someone").roles(role.name())))
                .andExpect(allowed ? status().isOk() : status().isForbidden());
    }

    @Test
    void wrongRoleGetsProblemDetail() throws Exception {
        mvc.perform(get("/api/admin/probe").with(user("karim").roles("BRAND")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.instance").value("/api/admin/probe"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"creator", "brand", "admin", "any"})
    void notLoggedInGets401(String area) throws Exception {
        mvc.perform(get("/api/" + area + "/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void publicAreaNeedsNoLogin() throws Exception {
        mvc.perform(get("/api/public/probe")).andExpect(status().isOk());
    }

    @Test
    void otherApiPathsAcceptAnyLoggedInRole() throws Exception {
        mvc.perform(get("/api/any/probe").with(user("lara").roles("CREATOR"))).andExpect(status().isOk());
    }

    @Test
    void changingRequestNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/creator/probe").with(user("lara").roles("CREATOR")))
                .andExpect(status().isForbidden());

        // What the SPA does: read the XSRF-TOKEN cookie and send the same value in the X-XSRF-TOKEN
        // header. Another site can make the browser send the cookie, but can't read it to set the header.
        String token = "test-token";
        mvc.perform(post("/api/creator/probe").with(user("lara").roles("CREATOR"))
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isOk());
    }
}
