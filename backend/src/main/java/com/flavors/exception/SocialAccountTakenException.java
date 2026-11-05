package com.flavors.exception;

/** This Instagram account is already connected to another creator (V2: platform + id is unique). Maps to HTTP 409. */
public class SocialAccountTakenException extends RuntimeException {

    public SocialAccountTakenException() {
        super("This Instagram account is already connected to another creator.");
    }
}
