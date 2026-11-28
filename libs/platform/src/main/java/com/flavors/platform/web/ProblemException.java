package com.flavors.platform.web;

import org.springframework.http.HttpStatus;

/** Throw it to answer with a ProblemDetail of the given status; {@code getMessage()} becomes the detail. */
public class ProblemException extends RuntimeException {

    private final HttpStatus status;

    public ProblemException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
