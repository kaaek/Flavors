package com.flavors.exception;

public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("This password-reset link is invalid, expired, or has already been used.");
    }
}
