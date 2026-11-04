package com.flavors.exception;

/** No such account among the creator's own accounts (someone else's counts as not found, NF-1). Maps to HTTP 404. */
public class SocialAccountNotFoundException extends RuntimeException {

    public SocialAccountNotFoundException() {
        super("This social account is not connected to your profile.");
    }
}
