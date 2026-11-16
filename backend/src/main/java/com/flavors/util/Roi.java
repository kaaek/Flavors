package com.flavors.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Return on investment of a campaign, as agreed with the customer:
 * {@code ROI = (attributed revenue - cost) / cost}, where the revenue is the bills before the
 * discount and the cost is the agreed price plus the discounts given. Amounts are integer cents.
 * Every screen that shows an ROI uses this class, so they can never disagree.
 */
public final class Roi {

    private Roi() {
    }

    /**
     * ROI as a percentage with one decimal, rounded half up: 150.0 means the revenue was 2.5 times
     * the cost. It can be negative. Returns null when there is nothing to measure against
     * (a cost that is zero or less), so the screen shows a dash.
     */
    public static BigDecimal percent(long revenueCents, long costCents) {
        if (costCents <= 0) {
            return null;
        }
        return BigDecimal.valueOf(revenueCents - costCents)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(costCents), 1, RoundingMode.HALF_UP);
    }
}
