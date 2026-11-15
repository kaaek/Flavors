package com.flavors.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.EscrowResponse;
import com.flavors.dto.PaymentResult;
import com.flavors.dto.PaymentStartResponse;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.enums.Role;
import com.flavors.exception.GlobalExceptionHandler;
import com.flavors.exception.PaymentAlreadyPaidException;
import com.flavors.exception.PaymentNotFoundException;
import com.flavors.exception.PaymentProviderException;
import com.flavors.service.PaymentService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The web side of paying (UC-F7.1): URLs, roles, the Idempotency-Key header and the callback/return answers. The rules are in PaymentServiceImplIT. */
@WebMvcTest({BrandPaymentController.class, PaymentCallbackController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    private static final CurrentUser KARIM = new CurrentUser(9, "karim@example.com", Role.BRAND, "{bcrypt}hash", true);
    private static final CurrentUser LARA = new CurrentUser(7, "lara@example.com", Role.CREATOR, "{bcrypt}hash", true);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentService payments;

    @Test
    void aBrandStartsThePaymentWithItsKey() throws Exception {
        given(payments.startPayment(9L, 42L, "key-1")).willReturn(new PaymentStartResponse("https://pay.example/c/1"));

        mvc.perform(post("/api/brand/campaigns/42/payment").with(user(KARIM)).with(csrf())
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://pay.example/c/1"));
    }

    @Test
    void payingAnAlreadyPaidCampaignAnswersConflict() throws Exception {
        given(payments.startPayment(9L, 42L, "key-2")).willThrow(new PaymentAlreadyPaidException());

        mvc.perform(post("/api/brand/campaigns/42/payment").with(user(KARIM)).with(csrf())
                        .header("Idempotency-Key", "key-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Already paid"));
    }

    @Test
    void aProviderThatDoesNotAnswerIsARetryableBadGateway() throws Exception {
        given(payments.startPayment(9L, 42L, "key-3")).willThrow(new PaymentProviderException("down"));

        mvc.perform(post("/api/brand/campaigns/42/payment").with(user(KARIM)).with(csrf())
                        .header("Idempotency-Key", "key-3"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void theIdempotencyKeyIsRequired() throws Exception {
        mvc.perform(post("/api/brand/campaigns/42/payment").with(user(KARIM)).with(csrf()))
                .andExpect(status().isBadRequest());
        then(payments).should(org.mockito.Mockito.never()).startPayment(9L, 42L, null);
    }

    @Test
    void aCreatorCannotStartAPayment() throws Exception {
        mvc.perform(post("/api/brand/campaigns/42/payment").with(user(LARA)).with(csrf())
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theBrandSeesTheEscrowStatus() throws Exception {
        given(payments.getEscrowForBrand(9L, 42L)).willReturn(new EscrowResponse(42L, EscrowStatus.HELD, 65_000L,
                Instant.parse("2030-01-02T10:00:00Z"), null, Instant.parse("2030-01-02T10:00:00Z")));

        mvc.perform(get("/api/brand/campaigns/42/escrow").with(user(KARIM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));
    }

    @Test
    void noEscrowYetIsNotFound() throws Exception {
        given(payments.getEscrowForBrand(9L, 42L)).willThrow(new PaymentNotFoundException("none"));

        mvc.perform(get("/api/brand/campaigns/42/escrow").with(user(KARIM)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theProviderCallbackOnlyTriggersACheck() throws Exception {
        mvc.perform(get("/api/public/payments/callback").param("externalId", "ref-9"))
                .andExpect(status().isOk());

        then(payments).should().reconcile("ref-9");
    }

    @Test
    void theReturnRedirectsToTheCampaignWithTheOutcome() throws Exception {
        given(payments.reconcile("ref-9")).willReturn(Optional.of(new PaymentResult(42L, PaymentTransactionStatus.SUCCEEDED, true)));

        mvc.perform(get("/api/public/payments/return").param("externalId", "ref-9"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/brand/campaigns/42?payment=held"));
    }

    @Test
    void aPaymentReceivedAfterTheWindowIsReportedAsReceivedNotHeld() throws Exception {
        given(payments.reconcile("ref-7")).willReturn(Optional.of(new PaymentResult(42L, PaymentTransactionStatus.SUCCEEDED, false)));

        mvc.perform(get("/api/public/payments/return").param("externalId", "ref-7"))
                .andExpect(header().string("Location", "/brand/campaigns/42?payment=received"));
    }

    @Test
    void aFailedPaymentReturnsToTheCampaignAsFailed() throws Exception {
        given(payments.reconcile("ref-8")).willReturn(Optional.of(new PaymentResult(42L, PaymentTransactionStatus.FAILED, false)));

        mvc.perform(get("/api/public/payments/return").param("externalId", "ref-8"))
                .andExpect(header().string("Location", "/brand/campaigns/42?payment=failed"));
    }
}
