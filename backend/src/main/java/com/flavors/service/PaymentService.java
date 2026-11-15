package com.flavors.service;

import com.flavors.dto.EscrowResponse;
import com.flavors.dto.PaymentResult;
import com.flavors.dto.PaymentStartResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Paying for a campaign and holding the money in escrow (UC-F7.1). The campaign is only ever changed under its
 * row lock, the same lock the creator's answers take (EEC-43), so a payment and Timer B cannot both win.
 */
public interface PaymentService {

    /**
     * The brand starts (or continues) paying for a campaign in AWAITING_PAYMENT. Idempotent per
     * {@code idempotencyKey} (NF-6): a retry returns the same attempt, and an open attempt is reused rather than
     * charged twice.
     *
     * @throws com.flavors.exception.CampaignNotFoundException   if the campaign is not this brand's
     * @throws com.flavors.exception.CampaignResponseStateException if the campaign can't be paid now
     */
    PaymentStartResponse startPayment(long brandId, long campaignId, String idempotencyKey);

    /**
     * Asks the provider for the payment's status and applies it: a paid payment is held and the campaign goes
     * ACTIVE, a failed one is recorded and the campaign stays AWAITING_PAYMENT. Calling it again changes nothing
     * (EX-F7.1.3). Empty when no payment has this reference.
     */
    Optional<PaymentResult> reconcile(String reference);

    /** The reconciliation sweep: re-checks payments that are still PENDING and older than {@code olderThan}. */
    int reconcileOpenPayments(Duration olderThan);

    /** Timer B (FR-F7.6): expires campaigns whose payment deadline has passed and nothing is held. */
    int expireOverduePayments();

    /** The escrow of a campaign of this brand. @throws com.flavors.exception.PaymentNotFoundException if none yet */
    EscrowResponse getEscrowForBrand(long brandId, long campaignId);

    /** The escrow of a campaign this creator received. @throws com.flavors.exception.PaymentNotFoundException if none yet */
    EscrowResponse getEscrowForCreator(long creatorId, long campaignId);
}
