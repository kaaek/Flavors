package com.flavors.exception;

/** The Meta app or the token encryption key is not set up in this environment yet. Maps to HTTP 503. */
public class SocialConnectionUnavailableException extends RuntimeException {

    public SocialConnectionUnavailableException() {
        super("Connecting Instagram is not available yet. Please try again later.");
    }
}
