package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.flavors.config.WhishProperties;
import com.flavors.dto.HostedPaymentRequest;
import com.flavors.dto.HostedPaymentSession;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.exception.PaymentProviderException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Whish adapter against a mocked HTTP server: nothing leaves the test. The paths and fields follow the
 * community notes (docs/README.md, "Connecting Whish"); the answers are read as the notes describe them.
 */
class WhishPaymentProviderTest {

    private static final String BASE = "https://whish.example.test/itel-service/api";

    private static WhishPaymentProvider provider(RestClient.Builder builder, MockRestServiceServer ignored) {
        return new WhishPaymentProvider(
                new WhishProperties("sandbox", BASE, "merchant-channel", "merchant-secret", "https://flavors.example.test",
                        "/payment/whish", "/payment/collect/status", Duration.ofSeconds(5), Duration.ofSeconds(15)),
                builder, JsonMapper.builder().build());
    }

    private static HostedPaymentRequest request(String reference) {
        return new HostedPaymentRequest(reference, 65_000L, "USD", "Flavors campaign 42",
                "https://flavors.example.test/api/public/payments/callback?externalId=" + reference,
                "https://flavors.example.test/api/public/payments/return?externalId=" + reference);
    }

    @Test
    void startSendsTheCredentialsAndReturnsTheCollectUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhishPaymentProvider whish = provider(builder, server);

        server.expect(requestTo(BASE + "/payment/whish"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("channel", "merchant-channel"))
                .andExpect(header("secret", "merchant-secret"))
                .andExpect(header("websiteurl", "https://flavors.example.test"))
                .andExpect(request -> org.assertj.core.api.Assertions.assertThat(
                        ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString())
                        .contains("\"externalId\":\"ref-1\"")
                        .contains("\"currency\":\"USD\"")
                        .contains("\"amount\":650"))
                .andRespond(withSuccess("{\"status\":true,\"data\":{\"collectUrl\":\"https://pay.whish.example/c/9\"}}",
                        MediaType.APPLICATION_JSON));

        HostedPaymentSession session = whish.startHostedPayment(request("ref-1"));

        assertThat(session.checkoutUrl()).isEqualTo("https://pay.whish.example/c/9");
        server.verify();
    }

    @Test
    void startSendsTheCallbackAndReturnUrlsWithTheReference() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhishPaymentProvider whish = provider(builder, server);
        String[] sent = new String[1];
        server.expect(requestTo(BASE + "/payment/whish"))
                .andExpect(request -> sent[0] = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                        .getBodyAsString())
                .andRespond(withSuccess("{\"status\":true,\"data\":{\"collectUrl\":\"https://pay.whish.example/c/10\"}}",
                        MediaType.APPLICATION_JSON));

        whish.startHostedPayment(request("ref-5"));

        Map<String, Object> body = JsonMapper.builder().build().readValue(sent[0], new TypeReference<>() {});
        assertThat(body)
                .containsEntry("successCallbackUrl",
                        "https://flavors.example.test/api/public/payments/callback?externalId=ref-5")
                .containsEntry("failureCallbackUrl",
                        "https://flavors.example.test/api/public/payments/callback?externalId=ref-5")
                .containsEntry("successRedirectUrl",
                        "https://flavors.example.test/api/public/payments/return?externalId=ref-5")
                .containsEntry("failureRedirectUrl",
                        "https://flavors.example.test/api/public/payments/return?externalId=ref-5");
        server.verify();
    }

    @Test
    void theCollectUrlAndStatusComeFromTheDataObjectOrTheTopLevelOnly() {
        assertThat(statusAnswering("{\"data\":{\"collectStatus\":\"success\"}}")).isEqualTo(ProviderPaymentStatus.PAID);
        assertThat(statusAnswering("{\"collectStatus\":\"success\"}")).isEqualTo(ProviderPaymentStatus.PAID);
        assertThat(statusAnswering("{\"data\":{\"other\":{\"collectStatus\":\"success\"}}}")).isEqualTo(ProviderPaymentStatus.PENDING);
    }

    @Test
    void startWithoutACollectUrlIsAProviderError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhishPaymentProvider whish = provider(builder, server);
        server.expect(requestTo(BASE + "/payment/whish"))
                .andRespond(withSuccess("{\"status\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> whish.startHostedPayment(request("ref-2")))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void onlyAnExactSuccessIsPaid() {
        assertThat(statusAnswering("{\"collectStatus\":\"success\"}")).isEqualTo(ProviderPaymentStatus.PAID);
        assertThat(statusAnswering("{\"data\":{\"collectStatus\":\"SUCCESS\"}}")).isEqualTo(ProviderPaymentStatus.PENDING);
        assertThat(statusAnswering("{\"collectStatus\":\"failed\"}")).isEqualTo(ProviderPaymentStatus.FAILED);
    }

    @Test
    void anUnpaidAnswerIsStillPending() {
        // status:false with code 500 means "not paid yet", not a failure.
        assertThat(statusAnswering("{\"status\":false,\"code\":\"500\"}")).isEqualTo(ProviderPaymentStatus.PENDING);
    }

    @Test
    void aServerErrorIsAProviderErrorNotAnAnswer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhishPaymentProvider whish = provider(builder, server);
        server.expect(requestTo(BASE + "/payment/collect/status")).andRespond(withServerError());

        assertThatThrownBy(() -> whish.queryStatus("ref-3", 65_000L, "USD"))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void aProviderThatNeverAnswersIsCutOffByTheTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            WhishProperties properties = new WhishProperties("sandbox", "http://127.0.0.1:" + silent.getLocalPort(),
                    "merchant-channel", "merchant-secret", "https://flavors.example.test",
                    "/payment/whish", "/payment/collect/status", Duration.ofMillis(300), Duration.ofMillis(300));
            WhishPaymentProvider whish = new WhishPaymentProvider(properties, JsonMapper.builder().build());

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThatThrownBy(
                    () -> whish.queryStatus("ref-6", 65_000L, "USD"))
                    .isInstanceOf(PaymentProviderException.class));
        }
    }

    @Test
    void aMissingCredentialStopsTheAdapterFromStarting() {
        assertThatThrownBy(() -> new WhishPaymentProvider(
                new WhishProperties("sandbox", BASE, "merchant-channel", "", "https://flavors.example.test",
                        "/payment/whish", "/payment/collect/status", Duration.ofSeconds(5), Duration.ofSeconds(15)),
                RestClient.builder(), JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHISH_SECRET");
    }

    private static ProviderPaymentStatus statusAnswering(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhishPaymentProvider whish = provider(builder, server);
        server.expect(requestTo(BASE + "/payment/collect/status"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return whish.queryStatus("ref-status", 65_000L, "USD");
    }
}
