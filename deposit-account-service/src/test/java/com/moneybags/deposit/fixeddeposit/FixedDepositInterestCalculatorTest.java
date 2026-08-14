package com.moneybags.deposit.fixeddeposit;

import com.moneybags.deposit.domain.DomainTypes.CompoundingFrequency;
import com.moneybags.deposit.domain.DomainTypes.TenureUnit;
import com.moneybags.deposit.fixeddeposit.calculation.FixedDepositInterestCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class FixedDepositInterestCalculatorTest {
    private final FixedDepositInterestCalculator calculator=new FixedDepositInterestCalculator();
    @Test void calculatesQuarterlyCompoundedMaturityWithoutFloatingPoint(){
        var result=calculator.calculate(new BigDecimal("100000.00"),new BigDecimal("6.75000000"),
                LocalDate.of(2026,8,13),12,TenureUnit.MONTH,CompoundingFrequency.QUARTERLY);
        assertThat(result.maturityDate()).isEqualTo(LocalDate.of(2027,8,13));
        assertThat(result.maturityAmount()).isEqualByComparingTo("106922.7897");
        assertThat(result.interest()).isEqualByComparingTo("6922.7897");
    }
    @Test void dailyAccrualUsesActual365AndBankersRounding(){
        assertThat(calculator.dailyAccrual(new BigDecimal("100000"),new BigDecimal("6.75")))
                .isEqualByComparingTo("18.4932");
    }
}
