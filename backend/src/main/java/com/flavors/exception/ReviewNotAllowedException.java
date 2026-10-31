package com.flavors.exception;

/** The admin's decision can't be applied now (EX-F20.1.2, EX-F20.1.3), e.g. the social account needs reconnection. */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException(String message) {
        super(message);
    }
}
