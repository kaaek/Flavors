package com.flavors.service.impl;

import com.flavors.service.PasswordResetMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    public SmtpPasswordResetMailSender(JavaMailSender mailSender,
                                       @Value("${flavors.mail.from}") String from,
                                       @Value("${flavors.app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public void sendResetLink(String recipient, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Reset your Flavors password");
        message.setText("Use this link within one hour to reset your password:\n\n"
                + baseUrl + "/reset-password?token=" + token
                + "\n\nIf you did not request this, you can ignore this email.");
        mailSender.send(message);
    }
}
