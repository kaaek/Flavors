package com.flavors.service.impl;

import com.flavors.dto.HostedPaymentRequest;
import com.flavors.dto.HostedPaymentSession;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.exception.PaymentProviderException;
import com.flavors.service.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Payments switched off (flavors.whish.mode=disabled, the production default until the merchant account is ready).
 * Nothing can be paid: starting a payment answers 503, and nothing is ever reported as paid.
 */
@Component
@ConditionalOnExpression("'${flavors.whish.mode:mock}' == 'disabled'")
public class UnavailablePaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "WHISH";
    }

    @Override
    public HostedPaymentSession startHostedPayment(HostedPaymentRequest request) {
        throw PaymentProviderException.disabled();
    }

    @Override
    public ProviderPaymentStatus queryStatus(String reference, long amountCents, String currency) {
        return ProviderPaymentStatus.PENDING;
    }
}
