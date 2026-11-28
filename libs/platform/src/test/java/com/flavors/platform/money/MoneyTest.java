package com.flavors.platform.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void percentRoundsHalvesUpAndPartsAddUp() {
        assertEquals(Money.ofCents(2), Money.ofCents(10).percent(15)); // 1.5 cents -> 2
        Money total = Money.ofCents(1_999);
        Money fee = total.percent(15);
        assertEquals(total, fee.plus(total.minus(fee)));
    }

    @Test
    void overflowThrowsInsteadOfWrapping() {
        assertThrows(ArithmeticException.class, () -> Money.ofCents(Long.MAX_VALUE).plus(Money.ofCents(1)));
    }
}
