package com.flavors.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CreatorStatusResponse;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.service.CreatorStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CreatorStatusController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CreatorStatusControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreatorStatusService statuses;

    @Test // TC-F20.1.4
    void aPendingCreatorSeesPendingApproval() throws Exception {
        when(statuses.statusOf(7L)).thenReturn(new CreatorStatusResponse(VerificationStatus.PENDING_APPROVAL, null));

        mvc.perform(get("/api/creator/status")
                        .with(user(new CurrentUser(7, "lara@example.com", Role.CREATOR, "x", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING_APPROVAL"));
    }

    @Test
    void aBrandCannotSeeCreatorStatus() throws Exception {
        mvc.perform(get("/api/creator/status")
                        .with(user(new CurrentUser(8, "b@example.com", Role.BRAND, "x", true))))
                .andExpect(status().isForbidden());
    }
}
