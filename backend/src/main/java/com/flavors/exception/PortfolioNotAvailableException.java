package com.flavors.exception;
 
/** The creator has no portfolio yet: an admin has not approved her profile (FR-F20.4). Maps to HTTP 409. */
public class PortfolioNotAvailableException extends RuntimeException {
 
    public PortfolioNotAvailableException() {
        super("Your portfolio is available after an admin approves your profile.");
    }
}