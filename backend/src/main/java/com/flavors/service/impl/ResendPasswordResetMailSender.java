package com.flavors.service.impl;

import com.flavors.service.PasswordResetMailSender;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Sends password-reset links over HTTPS on Railway Hobby (which blocks outbound SMTP). */
@Service
@ConditionalOnProperty(prefix = "flavors.mail", name = "provider", havingValue = "resend")
public class ResendPasswordResetMailSender implements PasswordResetMailSender {

    private final RestClient http;
    private final String from;
    private final String baseUrl;

    @Autowired
    public ResendPasswordResetMailSender(@Value("${RESEND_API_KEY:}") String apiKey,
                                         @Value("${flavors.mail.from}") String from,
                                         @Value("${flavors.app.base-url}") String baseUrl) {
        this(apiKey, from, baseUrl, RestClient.builder().requestFactory(requestFactory()));
    }

    // Package-private to test HTTP requests without a network connection.
    ResendPasswordResetMailSender(String apiKey, String from, String baseUrl, RestClient.Builder builder) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is required when FLAVORS_MAIL_PROVIDER=resend");
        }
        if (from == null || from.isBlank() || from.endsWith("@flavors.local")) {
            throw new IllegalStateException("Set FLAVORS_MAIL_FROM to an address on a verified sending domain");
        }
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("FLAVORS_APP_BASE_URL must use HTTPS for Resend password-reset links");
        }
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = builder.baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public void sendResetLink(String recipient, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        String content = "Use this link within one hour to reset your Flavors password:\n\n"
                + link + "\n\nIf you did not request this, you can ignore this email.";
        http.post().uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", List.of(recipient),
                        "subject", "Reset your Flavors password",
                        "text", content))
                .retrieve()
                .toBodilessEntity();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
}
