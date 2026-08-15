package com.moneybags.deposit.fixeddeposit.calculation;

import com.moneybags.deposit.domain.DomainTypes.CompoundingFrequency;
import com.moneybags.deposit.domain.DomainTypes.TenureUnit;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class FixedDepositInterestCalculator {
    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_EVEN);
    public Calculation calculate(BigDecimal principal, BigDecimal annualRate, LocalDate valueDate,
                                 int tenure, TenureUnit unit, CompoundingFrequency frequency) {
        LocalDate maturity = unit == TenureUnit.MONTH ? valueDate.plusMonths(tenure) : valueDate.plusDays(tenure);
        int compounds = switch (frequency) { case MONTHLY -> 12; case QUARTERLY -> 4; case HALF_YEARLY -> 2; case ANNUALLY -> 1; };
        BigDecimal ratePerPeriod=annualRate.divide(new BigDecimal("100"),MC).divide(BigDecimal.valueOf(compounds),MC);
        BigDecimal years=BigDecimal.valueOf(ChronoUnit.DAYS.between(valueDate,maturity)).divide(new BigDecimal("365"),MC);
        int completePeriods=years.multiply(BigDecimal.valueOf(compounds),MC).setScale(0,RoundingMode.DOWN).intValue();
        BigDecimal amount=principal.multiply(BigDecimal.ONE.add(ratePerPeriod).pow(completePeriods,MC),MC);
        BigDecimal remainingYears=years.subtract(BigDecimal.valueOf(completePeriods).divide(BigDecimal.valueOf(compounds),MC),MC);
        if (remainingYears.signum()>0) amount=amount.multiply(BigDecimal.ONE.add(annualRate.divide(new BigDecimal("100"),MC).multiply(remainingYears,MC)),MC);
        amount=amount.setScale(4,RoundingMode.HALF_EVEN);
        return new Calculation(maturity,amount.subtract(principal).setScale(4,RoundingMode.HALF_EVEN),amount);
    }
    public BigDecimal dailyAccrual(BigDecimal principal, BigDecimal annualRate) {
        return principal.multiply(annualRate,MC).divide(new BigDecimal("100"),MC)
                .divide(new BigDecimal("365"),MC).setScale(4,RoundingMode.HALF_EVEN);
    }
    public record Calculation(LocalDate maturityDate, BigDecimal interest, BigDecimal maturityAmount) {}
}
