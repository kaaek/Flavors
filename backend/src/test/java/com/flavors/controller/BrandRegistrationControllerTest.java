package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.BrandRegistrationResponse;
import com.flavors.enums.Role;
import com.flavors.exception.ApiExceptionHandler;
import com.flavors.exception.DuplicateEmailException;
import com.flavors.service.BrandRegistrationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BrandRegistrationController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class BrandRegistrationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrandRegistrationService registration;

    @Test
    void registersACompleteBrand() throws Exception {
        when(registration.register(any())).thenReturn(
                new BrandRegistrationResponse(7, "karim@example.com", Role.BRAND));

        mvc.perform(post("/api/public/auth/register/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest())
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("karim@example.com"))
                .andExpect(jsonPath("$.role").value("BRAND"));
    }

    @Test
    void missingBranchDoesNotCallTheService() throws Exception {
        mvc.perform(post("/api/public/auth/register/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName":"Karim's Kitchen",
                                  "contactPerson":"Karim",
                                  "email":"karim@example.com",
                                  "phone":"+961 1 234567",
                                  "password":"secret",
                                  "branches":[]
                                }
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.branches").value("Add at least one branch."));

        verifyNoInteractions(registration);
    }

    @Test
    void phoneWithLettersIsRejected() throws Exception {
        mvc.perform(post("/api/public/auth/register/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("+961 1 234567", "+961 ABC"))
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").value("Enter a valid phone number."));

        verifyNoInteractions(registration);
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(post("/api/public/auth/register/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("karim@example.com", "not-an-email"))
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Enter a valid email address."));

        verifyNoInteractions(registration);
    }

    @Test
    void duplicateEmailTellsTheUserToLogInOrUseAnotherEmail() throws Exception {
        doThrow(new DuplicateEmailException()).when(registration).register(any());

        mvc.perform(post("/api/public/auth/register/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest())
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "An account already uses this email. Log in or use another email."));
    }

    private static String validRequest() {
        return """
                {
                  "businessName":"Karim's Kitchen",
                  "contactPerson":"Karim",
                  "email":"karim@example.com",
                  "phone":"+961 1 234567",
                  "password":"secret",
                  "branches":[
                    {"name":"Hamra","location":"Beirut"}
                  ]
                }
                """;
    }
}
