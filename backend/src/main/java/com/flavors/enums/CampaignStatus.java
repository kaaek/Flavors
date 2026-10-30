package com.flavors.enums;
 
/**
 * Where a campaign is in its life (the values of the status check in V3). Only COMPLETED
 * campaigns can be featured on a portfolio (EX-F1.2.1).
 */
public enum CampaignStatus {
    DRAFT,
    AWAITING_CREATOR_RESPONSE,
    COUNTERED,
    REJECTED,
    DECLINED,
    EXPIRED,
    AWAITING_PAYMENT,
    ACTIVE,
    PROOF_SUBMITTED,
    CORRECTIONS_REQUESTED,
    DISPUTED,
    COMPLETED,
    CANCELLED,
    REMOVED
}