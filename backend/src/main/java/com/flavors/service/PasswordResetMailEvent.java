package com.flavors.service;

/** Carries a reset link from the committing database transaction to the mail boundary. */
public record PasswordResetMailEvent(Long userId, String email, String rawToken) {
}
