package com.moneybags.deposit.fixeddeposit.integration;

import com.moneybags.deposit.domain.DomainTypes.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface FixedDepositProductGateway {
    ProductTerms resolve(String productCode, Long productVersion, BigDecimal principal, String currency,
                         int tenureValue, TenureUnit tenureUnit, InterestPayoutFrequency payoutFrequency,
                         LocalDate valueDate, Integer customerAge, String customerType,
                         String customerCategory, boolean kycVerified);

    record ProductTerms(String productCode, Long productVersion, String productName, String rateSlabCode,
                        String interestPolicyVersion, BigDecimal annualRate, String calculationMethod,
                        CompoundingFrequency compoundingFrequency, InterestPayoutFrequency payoutFrequency,
                        DayCountConvention dayCountConvention, String ruleSnapshotJson) {}
}
