package com.flavors.exception;

/** The private proof store could not safely complete a read or write. */
public class ProofStorageException extends RuntimeException {

    public ProofStorageException(String message) {
        super(message);
    }

    public ProofStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
