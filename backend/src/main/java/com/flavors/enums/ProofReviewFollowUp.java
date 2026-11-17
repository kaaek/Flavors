package com.flavors.enums;

/** Required next workflow action; payment/dispute actions belong to UC-F7.2/UC-F8.1. */
public enum ProofReviewFollowUp {
    RELEASE_PAYMENT,
    FREEZE_PAYMENT_AND_OPEN_DISPUTE,
    RESUBMIT_PROOF
}
