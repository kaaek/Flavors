package com.flavors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CreatorRegistrationResponse;
import com.flavors.enums.Role;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.exception.DuplicateEmailException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.service.CreatorRegistrationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(CreatorRegistrationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CreatorRegistrationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreatorRegistrationService registration;

    @Test // TC-F19.1.1
    void registersACompleteCreatorAsPendingApproval() throws Exception {
        when(registration.register(any())).thenReturn(new CreatorRegistrationResponse(
                7, "lara@example.com", Role.CREATOR, VerificationStatus.PENDING_APPROVAL));

        send(validRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("CREATOR"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING_APPROVAL"));
    }

    @Test // TC-F19.1.2
    void emptyFoodCategoryIsRefusedAndShown() throws Exception {
        send(validRequest().replace("\"Street food\"", "\"\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.foodCategory").value("Food category is required."));

        verifyNoInteractions(registration);
    }

    @Test // TC-F19.1.3
    void duplicateEmailTellsTheUserToLogInOrUseAnotherEmail() throws Exception {
        doThrow(new DuplicateEmailException()).when(registration).register(any());

        send(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "An account already uses this email. Log in or use another email."));
    }

    @Test // TC-F19.1.4
    void minimumPriceAboveMaximumIsRefused() throws Exception {
        doThrow(new ValidationFailedException("priceMinCents", "Minimum price can't be above the maximum price."))
                .when(registration).register(any());

        send(validRequest().replace("\"priceMinCents\": 10000", "\"priceMinCents\": 30000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priceMinCents").value("Minimum price can't be above the maximum price."));
    }

    @Test // TC-F19.1.5
    void noSocialAccountIsRefused() throws Exception {
        send(validRequest().replaceAll("\"socialAccounts\":\\s*\\[[^\\]]*\\]", "\"socialAccounts\": []"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.socialAccounts").value("Connect at least one social account."));

        verifyNoInteractions(registration);
    }

    @Test
    void invalidEmailIsRefused() throws Exception {
        send(validRequest().replace("lara@example.com", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Enter a valid email address."));

        verifyNoInteractions(registration);
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post("/api/public/auth/register/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(new Cookie("XSRF-TOKEN", "token"))
                .header("X-XSRF-TOKEN", "token"));
    }

    private static String validRequest() {
        return """
                {
                  "name": "Lara",
                  "email": "lara@example.com",
                  "password": "correct horse",
                  "foodCategory": "Street food",
                  "location": "Beirut",
                  "priceMinCents": 10000,
                  "priceMaxCents": 30000,
                  "bio": "I eat for a living.",
                  "socialAccounts": [
                    {"platform": "instagram", "externalId": "lara.eats"}
                  ]
                }
                """;
    }
}
