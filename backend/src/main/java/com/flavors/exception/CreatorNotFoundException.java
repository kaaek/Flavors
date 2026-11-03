package com.flavors.exception;

public class CreatorNotFoundException extends RuntimeException {

    public CreatorNotFoundException(long id) {
        super("Creator " + id + " was not found.");
    }
}
