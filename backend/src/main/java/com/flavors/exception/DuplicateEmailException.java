package com.flavors.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account already uses this email. Log in or use another email.");
    }
}
