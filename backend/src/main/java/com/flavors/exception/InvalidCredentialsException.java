package com.flavors.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("The email or password is incorrect.");
    }
}
