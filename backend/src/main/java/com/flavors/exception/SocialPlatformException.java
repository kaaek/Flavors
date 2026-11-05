package com.flavors.exception;

/**
 * Instagram couldn't be reached, or refused a request (EX-F19.4.1, EX-F1.3.1). The message never contains
 * a token or a URL, because Instagram URLs carry the token. Maps to HTTP 502.
 */
public class SocialPlatformException extends RuntimeException {

    public SocialPlatformException(String message) {
        super(message);
    }
}
