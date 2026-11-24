package com.flavors.exception;

/**
 * The creator asked to remove a social account that must stay: it is still connected, or its figures feed a
 * campaign's results. The message says which, and is shown to her. Maps to HTTP 409.
 */
public class SocialAccountRemovalNotAllowedException extends RuntimeException {

    public SocialAccountRemovalNotAllowedException(String message) {
        super(message);
    }
}
