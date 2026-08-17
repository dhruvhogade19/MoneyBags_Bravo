package com.moneybags.deposit.fixeddeposit.integration;

import com.moneybags.deposit.domain.DomainTypes.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name="moneybags.deposit.stub-upstream-clients", havingValue="true", matchIfMissing=true)
public class StubFixedDepositProductGateway implements FixedDepositProductGateway {
    @Override public ProductTerms resolve(String code, Long version, BigDecimal principal, String currency,
                                          int tenure, TenureUnit unit, InterestPayoutFrequency payout, LocalDate valueDate,
                                          Integer customerAge, BigDecimal monthlyIncome, String customerType, String customerCategory,
                                          boolean kycVerified) {
        BigDecimal rate = tenure >= 12 ? new BigDecimal("6.75000000") : new BigDecimal("5.50000000");
        return new ProductTerms(code, version, code.replace('-', ' '), "STUB-"+tenure+unit,
                "V1", rate, "COMPOUND_INTEREST", CompoundingFrequency.QUARTERLY, payout,
                DayCountConvention.ACTUAL_365, "{\"source\":\"stub\"}");
    }
}
