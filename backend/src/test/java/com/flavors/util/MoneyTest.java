package com.flavors.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAndSubtracts() {
        Money total = Money.ofCents(1050).plus(Money.ofCents(250));

        assertThat(total).isEqualTo(Money.ofCents(1300));
        assertThat(total.minus(Money.ofCents(1300))).isEqualTo(Money.ZERO);
    }

    @Test
    void percentOfAWholeAmountIsExact() {
        assertThat(Money.ofCents(100_000).percent(15)).isEqualTo(Money.ofCents(15_000));
        assertThat(Money.ofCents(12_345).percent(0)).isEqualTo(Money.ZERO);
        assertThat(Money.ofCents(12_345).percent(100)).isEqualTo(Money.ofCents(12_345));
    }

    @Test
    void percentRoundsToTheNearestCentWithHalvesGoingUp() {
        assertThat(Money.ofCents(333).percent(15)).isEqualTo(Money.ofCents(50));  // 49.95
        assertThat(Money.ofCents(10).percent(15)).isEqualTo(Money.ofCents(2));    // 1.5
        assertThat(Money.ofCents(5).percent(10)).isEqualTo(Money.ofCents(1));     // 0.5
        assertThat(Money.ofCents(4).percent(10)).isEqualTo(Money.ZERO);           // 0.4
        assertThat(Money.ofCents(-10).percent(15)).isEqualTo(Money.ofCents(-2));  // halves go away from zero
    }

    @Test
    void feeAndNetAlwaysAddUpToTheTotal() {
        for (long cents = 0; cents < 2_000; cents++) {
            Money amount = Money.ofCents(cents);
            Money fee = amount.percent(15);
            Money net = amount.minus(fee);

            assertThat(fee.plus(net)).isEqualTo(amount);
        }
    }

    @Test
    void comparesByAmount() {
        assertThat(Money.ofCents(100)).isLessThan(Money.ofCents(101)).isEqualByComparingTo(Money.ofCents(100));
    }

    @Test
    void overflowThrowsInsteadOfWrapping() {
        Money max = Money.ofCents(Long.MAX_VALUE);

        assertThatThrownBy(() -> max.plus(Money.ofCents(1))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> max.percent(200)).isInstanceOf(ArithmeticException.class);
    }
}
