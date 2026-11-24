package com.flavors.exception;

/** A brand without a branch can't create a campaign (EX-F3.1.3). */
public class NoBranchException extends RuntimeException {

    public NoBranchException() {
        super("You need at least one branch before you can create a campaign.");
    }
}
