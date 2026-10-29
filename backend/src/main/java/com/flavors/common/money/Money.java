package com.flavors.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of US dollars, held as whole cents in a {@code long} (the {@code *_cents} columns and the
 * API both use cents). Floating-point numbers are never used for money, so fees and refunds don't drift.
 *
 * <p>Arithmetic that would overflow throws instead of wrapping around silently.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    /**
     * {@code percent}% of this amount, rounded to the nearest cent with halves going up (HALF_UP):
     * 15% of $0.10 is 1.5 cents, so $0.02. Use it for the platform fee, refunds and ROI.
     *
     * <p>When splitting an amount in two (a fee and the creator's net, a dispute's creator and brand
     * shares), compute one part with this and get the other by {@link #minus}, so the parts always add
     * up to the total and no cent is created or lost.
     */
    public Money percent(int percent) {
        BigDecimal result = BigDecimal.valueOf(cents)
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Money(result.longValueExact());
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }
}
