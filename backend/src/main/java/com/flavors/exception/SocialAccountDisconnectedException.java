package com.flavors.exception;

/** The creator has no connected social account, so she can't accept a new campaign (FT-68). */
public class SocialAccountDisconnectedException extends RuntimeException {
    public SocialAccountDisconnectedException() {
        super("Reconnect your Instagram account before you accept a campaign.");
    }
}