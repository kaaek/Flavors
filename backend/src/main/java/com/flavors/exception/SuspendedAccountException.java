package com.flavors.exception;

public class SuspendedAccountException extends RuntimeException {

    public SuspendedAccountException() {
        super("This account is suspended.");
    }
}
