package com.moneybags.deposit.closure;

import com.moneybags.deposit.closure.calculation.PrematureClosureCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PrematureClosureCalculatorTest {
    private final PrematureClosureCalculator calculator = new PrematureClosureCalculator();

    @Test
    void calculatesReducedRateAndPayoutForCompletedHoldingDays() {
        var result = calculator.calculate(new BigDecimal("10000.00"), new BigDecimal("6.75"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("6.75"), new BigDecimal("1.00"), BigDecimal.ZERO);

        assertThat(result.holdingDays()).isEqualTo(30);
        assertThat(result.finalRate()).isEqualByComparingTo("5.75000000");
        assertThat(result.recalculatedInterest()).isEqualByComparingTo("47.2603");
        assertThat(result.netPayout()).isEqualByComparingTo("10047.2603");
    }

    @Test
    void recoversInterestAlreadyPaidAboveRecalculatedEntitlement() {
        var result = calculator.calculate(new BigDecimal("10000.00"), new BigDecimal("6.75"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new BigDecimal("6.75"), new BigDecimal("1.00"), new BigDecimal("60.00"));

        assertThat(result.interestRecovery()).isEqualByComparingTo("12.7397");
        assertThat(result.netInterest()).isEqualByComparingTo("-12.7397");
        assertThat(result.netPayout()).isEqualByComparingTo("9987.2603");
    }
}
