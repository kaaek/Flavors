package com.flavors.exception;

/** The logged-in creator has no creator profile yet, so there is nothing to attach the account to. Maps to HTTP 409. */
public class CreatorProfileMissingException extends RuntimeException {

    public CreatorProfileMissingException() {
        super("Finish your creator registration before connecting a social account.");
    }
}
