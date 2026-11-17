package com.flavors.service.impl;

import com.flavors.dto.PayoutRequest;
import com.flavors.dto.PayoutResult;
import com.flavors.service.PayoutProvider;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory simulation only: never transfers money. A lost simulator ledger yields UNKNOWN, never a blind retry. */
public class SimulatedPayoutProvider implements PayoutProvider {
    private final ConcurrentHashMap<String, PayoutRequest> ledger = new ConcurrentHashMap<>();

    public String name() { return "SIMULATED"; }
    public boolean available() { return true; }
    public boolean simulated() { return true; }

    public PayoutResult submit(PayoutRequest request) {
        ledger.compute(request.operationKey(), (key, previous) -> {
            if (previous != null && !previous.equals(request)) {
                throw new IllegalStateException("Simulation identity cannot change.");
            }
            return request;
        });
        return confirmed(request);
    }

    public PayoutResult query(PayoutRequest request, String reference) {
        return request.equals(ledger.get(request.operationKey())) ? confirmed(request) : PayoutResult.unknown(request);
    }

    private PayoutResult confirmed(PayoutRequest request) {
        return new PayoutResult(PayoutResult.Outcome.CONFIRMED, request,
                "simulated:" + request.operationKey(), false);
    }
}
