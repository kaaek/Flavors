package com.flavors.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoiTest {

    @Test
    void isRevenueMinusCostOverCostAsAPercentage() {
        // Cost 60 000, revenue 150 000: (150 000 - 60 000) / 60 000 = 1.5.
        assertThat(Roi.percent(150_000, 60_000)).isEqualByComparingTo("150.0");
    }

    @Test
    void isZeroWhenTheRevenueEqualsTheCost() {
        assertThat(Roi.percent(60_000, 60_000)).isEqualByComparingTo("0.0");
    }

    @Test
    void canBeNegative() {
        // (10 000 - 51 000) / 51 000 = -0.80392...
        assertThat(Roi.percent(10_000, 51_000)).isEqualByComparingTo("-80.4");
    }

    @Test
    void roundsHalfUpToOneDecimal() {
        // (10 005 - 10 000) / 10 000 = 0.05 %, which is 0.1 when rounded half up.
        assertThat(Roi.percent(10_005, 10_000)).isEqualByComparingTo("0.1");
    }

    @Test
    void isNullWhenThereIsNoCostToMeasureAgainst() {
        assertThat(Roi.percent(10_000, 0)).isNull();
        assertThat(Roi.percent(10_000, -5)).isNull();
    }
}
