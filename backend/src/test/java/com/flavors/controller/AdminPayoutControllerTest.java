
package com.flavors.controller;

import com.flavors.service.EscrowPayoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.flavors.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

@WebMvcTest(AdminPayoutController.class)
@Import(SecurityConfig.class)class AdminPayoutControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EscrowPayoutService payouts;

    private static final String URL =
            "/api/admin/payouts/42/review-evidence";

    private static final String VALID_BODY = """
            {
              "evidenceReference": "provider-case-123",
              "finding": "Provider investigation recorded"
            }
            """;

    @Test
    void adminCanRecordReviewEvidence() throws Exception {
        mvc.perform(post(URL)
                .with(user("admin").roles("ADMIN", "ADMIN_MFA"))
                .with(csrf())
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isNoContent());

        verify(payouts).recordManualReviewEvidence(
                42L,
                "provider-case-123",
                "Provider investigation recorded"
        );
    }

    @Test
    void brandCannotRecordReviewEvidence() throws Exception {
        mvc.perform(post(URL)
                .with(user("brand").roles("BRAND"))
                .with(csrf())
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isForbidden());

        verifyNoInteractions(payouts);
    }

    @Test
    void anonymousUserCannotRecordReviewEvidence() throws Exception {
        mvc.perform(post(URL)
                .with(csrf())
                .contentType("application/json")
                .content(VALID_BODY))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(payouts);
    }

    @Test
    void blankEvidenceIsRejected() throws Exception {
        mvc.perform(post(URL)
                .with(user("admin").roles("ADMIN", "ADMIN_MFA"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                        {
                          "evidenceReference": "",
                          "finding": "Investigation complete"
                        }
                        """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(payouts);
    }
}
