package com.flavors.exception;
public class SavedCreatorNotFoundException extends RuntimeException {
    public SavedCreatorNotFoundException(String message) {
        super(message);
    }
}
