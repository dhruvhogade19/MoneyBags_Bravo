package com.moneybags.deposit.closure.calculation;

import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Component
public class PrematureClosureCalculator {
    private static final MathContext MC=new MathContext(24,RoundingMode.HALF_EVEN);
    public Calculation calculate(BigDecimal principal,BigDecimal bookedRate,LocalDate valueDate,
        LocalDate closureDate,BigDecimal applicableRate,BigDecimal penaltyRate,BigDecimal alreadyPaid){
        long days=ChronoUnit.DAYS.between(valueDate,closureDate);
        BigDecimal finalRate=applicableRate.subtract(penaltyRate).max(BigDecimal.ZERO).setScale(8,RoundingMode.HALF_EVEN);
        BigDecimal interest=principal.multiply(finalRate,MC).divide(new BigDecimal("100"),MC)
            .multiply(BigDecimal.valueOf(days),MC).divide(new BigDecimal("365"),MC).setScale(4,RoundingMode.HALF_EVEN);
        BigDecimal recovery=alreadyPaid.subtract(interest).max(BigDecimal.ZERO).setScale(4,RoundingMode.HALF_EVEN);
        BigDecimal netInterest=interest.subtract(alreadyPaid).setScale(4,RoundingMode.HALF_EVEN);
        BigDecimal netPayout=principal.add(netInterest).max(BigDecimal.ZERO).setScale(4,RoundingMode.HALF_EVEN);
        return new Calculation(days,finalRate,interest,recovery,netInterest,netPayout);
    }
    public record Calculation(long holdingDays,BigDecimal finalRate,BigDecimal recalculatedInterest,
        BigDecimal interestRecovery,BigDecimal netInterest,BigDecimal netPayout) {}
}
