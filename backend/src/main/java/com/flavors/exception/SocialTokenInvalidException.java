package com.flavors.exception;

/** The creator revoked the access, or the token expired: the account needs reconnection (TC-F19.4.3). */
public class SocialTokenInvalidException extends SocialPlatformException {

    public SocialTokenInvalidException() {
        super("The Instagram access was revoked or has expired.");
    }
}
