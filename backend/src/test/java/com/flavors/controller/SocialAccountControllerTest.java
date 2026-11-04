package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.SocialAccountResponse;
import com.flavors.enums.Role;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.CreatorProfileMissingException;
import com.flavors.exception.SocialAccountNotFoundException;
import com.flavors.exception.SocialAccountTakenException;
import com.flavors.exception.SocialConnectionUnavailableException;
import com.flavors.exception.SocialPlatformException;
import com.flavors.service.SocialAccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web side of connecting a social account (EEC-33): URLs, who may call them, the Instagram round trip
 * with its state check, and the error bodies. The rules themselves are tested in SocialAccountServiceImplIT;
 * the service is a mock here.
 */
@WebMvcTest(SocialAccountController.class)
@Import(SecurityConfig.class)
class SocialAccountControllerTest {

    private static final CurrentUser LARA = new CurrentUser(7, "lara@example.com", Role.CREATOR, "{bcrypt}hash", true);
    private static final CurrentUser KARIM = new CurrentUser(9, "karim@example.com", Role.BRAND, "{bcrypt}hash", true);

    private static final String CALLBACK = "/api/creator/social/instagram/callback";

    private static final SocialAccountResponse ACCOUNT = new SocialAccountResponse(3, "instagram",
            SocialAccountStatus.CONNECTED, 12000L, new BigDecimal("3.00"), Instant.parse("2026-11-03T10:00:00Z"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SocialAccountService service;

    // ---- List ----

    @Test
    void listShowsHerAccounts() throws Exception {
        given(service.myAccounts(7L)).willReturn(List.of(ACCOUNT));

        mvc.perform(get("/api/creator/social").with(user(LARA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].platform").value("instagram"))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$[0].followers").value(12000));
    }

    @Test
    void onlyCreatorsReachIt() throws Exception {
        mvc.perform(get("/api/creator/social")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/creator/social").with(user(KARIM))).andExpect(status().isForbidden());
        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1")).andExpect(status().isUnauthorized());
        then(service).shouldHaveNoInteractions();
    }

    // ---- Step 1: start ----

    @Test
    void connectGivesInstagramsPageAndRemembersTheState() throws Exception {
        given(service.instagramAuthorizationUrl(eq(7L), anyString())).willReturn("https://www.instagram.com/oauth/authorize?x=1");
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/creator/social/instagram/connect").with(user(LARA)).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value("https://www.instagram.com/oauth/authorize?x=1"));

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        then(service).should().instagramAuthorizationUrl(eq(7L), state.capture());
        assertThat(state.getValue()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(session.getAttribute(SocialAccountController.STATE_ATTRIBUTE)).isEqualTo(state.getValue());
    }

    @Test
    void connectNeedsTheCsrfToken() throws Exception {
        mvc.perform(post("/api/creator/social/instagram/connect").with(user(LARA))).andExpect(status().isForbidden());
        then(service).shouldHaveNoInteractions();
    }

    @Test
    void connectSays503UntilInstagramIsSetUp() throws Exception {
        given(service.instagramAuthorizationUrl(eq(7L), anyString())).willThrow(new SocialConnectionUnavailableException());

        mvc.perform(post("/api/creator/social/instagram/connect").with(user(LARA)).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Instagram not set up"));
    }

    @Test
    void connectSays409WithoutACreatorProfile() throws Exception {
        given(service.instagramAuthorizationUrl(eq(7L), anyString())).willThrow(new CreatorProfileMissingException());

        mvc.perform(post("/api/creator/social/instagram/connect").with(user(LARA)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Creator profile missing"));
    }

    // ---- Step 3: Instagram sends her back ----

    // TC-F19.4.1 at HTTP level
    @Test
    void aMatchingCallbackConnectsTheAccount() throws Exception {
        given(service.connectInstagram(7L, "abc")).willReturn(ACCOUNT);
        MockHttpSession session = startedWith("s1");

        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1").with(user(LARA)).session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/creator/social?instagram=connected"));

        then(service).should().connectInstagram(7L, "abc");
        assertThat(session.getAttribute(SocialAccountController.STATE_ATTRIBUTE)).isNull();
    }

    // TC-F19.4.2 at HTTP level: she cancels on Instagram, nothing is saved.
    @Test
    void cancellingOnInstagramSavesNothing() throws Exception {
        mvc.perform(get(CALLBACK).param("error", "access_denied").param("error_reason", "user_denied")
                        .param("state", "s1").with(user(LARA)).session(startedWith("s1")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/creator/social?instagram=denied"));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void aCallbackWithTheWrongStateIsRefused() throws Exception {
        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "someone-elses")
                        .with(user(LARA)).session(startedWith("s1")))
                .andExpect(redirectedUrl("/creator/social?instagram=expired"));

        // No connection was started in this session at all.
        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1")
                        .with(user(LARA)).session(new MockHttpSession()))
                .andExpect(redirectedUrl("/creator/social?instagram=expired"));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void aCallbackWorksOnlyOnce() throws Exception {
        given(service.connectInstagram(7L, "abc")).willReturn(ACCOUNT);
        MockHttpSession session = startedWith("s1");

        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1").with(user(LARA)).session(session))
                .andExpect(redirectedUrl("/creator/social?instagram=connected"));
        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1").with(user(LARA)).session(session))
                .andExpect(redirectedUrl("/creator/social?instagram=expired"));

        then(service).should(times(1)).connectInstagram(7L, "abc");
    }

    // EX-F19.4.1: the connection fails; she is told and can try again.
    @Test
    void aFailedConnectionSendsHerBackToTryAgain() throws Exception {
        given(service.connectInstagram(7L, "abc")).willThrow(new SocialPlatformException("down"));

        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1").with(user(LARA)).session(startedWith("s1")))
                .andExpect(redirectedUrl("/creator/social?instagram=failed"));
    }

    @Test
    void anAccountOfAnotherCreatorIsReported() throws Exception {
        given(service.connectInstagram(7L, "abc")).willThrow(new SocialAccountTakenException());

        mvc.perform(get(CALLBACK).param("code", "abc").param("state", "s1").with(user(LARA)).session(startedWith("s1")))
                .andExpect(redirectedUrl("/creator/social?instagram=taken"));
    }

    // ---- Disconnect ----

    @Test
    void disconnectAnswers204() throws Exception {
        mvc.perform(delete("/api/creator/social/3").with(user(LARA)).with(csrf()))
                .andExpect(status().isNoContent());

        then(service).should().disconnect(7L, 3L);
    }

    @Test
    void disconnectingSomeoneElsesAccountAnswers404() throws Exception {
        willThrow(new SocialAccountNotFoundException()).given(service).disconnect(7L, 99L);

        mvc.perform(delete("/api/creator/social/99").with(user(LARA)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Social account not found"));
    }

    /** A session in which Lara started a connection with this state. */
    private static MockHttpSession startedWith(String state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SocialAccountController.STATE_ATTRIBUTE, state);
        return session;
    }
}
