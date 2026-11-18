package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class ResendPasswordResetMailSenderTest {

    @Test
    void sendsTheResetLinkAsHttpsApiRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var sender = new ResendPasswordResetMailSender("test-only-api-key", "Flavors <reset@mail.flavors.example>",
                "https://flavors-staging.example", builder);

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-only-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    String payload = ((MockClientHttpRequest) request).getBodyAsString();
                    org.assertj.core.api.Assertions.assertThat(payload)
                            .contains("reset@mail.flavors.example")
                            .contains("creator@example.test")
                            .contains("/reset-password?token=test-token")
                            .contains("Reset your Flavors password");
                })
                .andRespond(withSuccess("{\"id\":\"message-id\"}", MediaType.APPLICATION_JSON));

        sender.sendResetLink("creator@example.test", "test-token");
        server.verify();
    }

    @Test
    void apiFailurePropagatesToExistingListenerForLogging() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var sender = new ResendPasswordResetMailSender("test-only-api-key", "reset@mail.flavors.example",
                "https://flavors-staging.example", builder);
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

        assertThatThrownBy(() -> sender.sendResetLink("creator@example.test", "test-token"))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void failsFastForMissingSettings() {
        assertThatThrownBy(() -> new ResendPasswordResetMailSender("", "reset@mail.flavors.example",
                "https://flavors-staging.example", RestClient.builder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RESEND_API_KEY");
        assertThatThrownBy(() -> new ResendPasswordResetMailSender("test-key", "no-reply@flavors.local",
                "https://flavors-staging.example", RestClient.builder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("FLAVORS_MAIL_FROM");
        assertThatThrownBy(() -> new ResendPasswordResetMailSender("test-key", "reset@mail.flavors.example",
                "http://localhost:5173", RestClient.builder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("FLAVORS_APP_BASE_URL");
    }
}
