package com.flavors.service;

import com.flavors.dto.HostedPaymentRequest;
import com.flavors.dto.HostedPaymentSession;
import com.flavors.enums.ProviderPaymentStatus;

/**
 * The payment provider behind the escrow (FR-F7.2). The rest of the code only sees this interface, so the
 * provider can change without touching the campaign rules.
 *
 * <p>Card data is entered on the provider's hosted page, never in Flavors. The provider reports a payment
 * through a callback, but a callback (and the browser's return) is only a hint: {@link #queryStatus} is the answer
 * Flavors acts on.
 */
public interface PaymentProvider {

    /** Recorded in escrow_payment.provider, e.g. WHISH. */
    String name();

    /** Opens a hosted payment page for one attempt. */
    HostedPaymentSession startHostedPayment(HostedPaymentRequest request);

    /**
     * Asks the provider whether the attempt with this reference has been paid. Must be safe to call any number of
     * times; it changes nothing on the provider's side.
     *
     * @throws com.flavors.exception.PaymentProviderException if the provider cannot be reached or answers badly
     */
    ProviderPaymentStatus queryStatus(String reference, long amountCents, String currency);
}
