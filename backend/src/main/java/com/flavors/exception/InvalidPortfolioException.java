package com.flavors.exception;
 
/** A portfolio edit broke a rule (min price above max, a campaign that is not completed, ...). Maps to HTTP 400. */
public class InvalidPortfolioException extends RuntimeException {
 
    public InvalidPortfolioException(String message) {
        super(message);
    }
}