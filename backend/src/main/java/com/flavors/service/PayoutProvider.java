package com.flavors.service;

import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;

/** Separate from collection. Implementations must deduplicate submits by operationKey and support reconciliation. */
public interface PayoutProvider {
    String name();
    boolean available();
    boolean simulated();
    PayoutResult submit(PayoutRequest request);
    PayoutResult query(PayoutRequest request, String providerRef);
}
