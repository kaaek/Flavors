package com.flavors.exception;
public class CreatorNotApprovedException extends RuntimeException {
    public CreatorNotApprovedException() {
        super("Creator is not approved");
    }
}
