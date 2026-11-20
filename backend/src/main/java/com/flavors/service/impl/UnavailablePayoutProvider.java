package com.flavors.service.impl;

import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;
import com.flavors.service.PayoutProvider;

/** No network, credentials, invented Whish contract, or financial confirmation. */
public class UnavailablePayoutProvider implements PayoutProvider {
    public String name() { return "UNAVAILABLE"; }
    public boolean available() { return false; }
    public boolean simulated() { return false; }
    public PayoutResult submit(PayoutRequest request) { return PayoutResult.unknown(request); }
    public PayoutResult query(PayoutRequest request, String reference) { return PayoutResult.unknown(request); }
}
