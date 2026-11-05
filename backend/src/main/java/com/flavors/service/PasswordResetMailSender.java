package com.flavors.service;

public interface PasswordResetMailSender {

    void sendResetLink(String recipient, String token);
}
