package com.moneybags.deposit.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubBankingReferenceGateway implements BankingReferenceGateway {
    @Override
    public ValidationResult validateAccountOpening(String customerId, String productId, Long productVersion,
                                                   String currency, BigDecimal openingAmount) {
        String normalizedProductId = productId.toUpperCase();
        String accountType = normalizedProductId.contains("CURRENT") || normalizedProductId.startsWith("CUR")
                ? "CURRENT"
                : normalizedProductId.contains("SAVING") || normalizedProductId.startsWith("SAV")
                ? "SAVINGS" : "UNSUPPORTED";
        boolean eligible = !customerId.toUpperCase().contains("REJECT")
                && !productId.toUpperCase().contains("INACTIVE")
                && !"UNSUPPORTED".equals(accountType);
        return new ValidationResult(eligible, eligible ? "ELIGIBLE" : "NOT_ELIGIBLE",
                productId.replace('-', ' '), accountType, OffsetDateTime.now());
    }

    @Override
    public ValidationResult validateCustomerEligibility(String customerId) {
        boolean eligible = !customerId.toUpperCase().contains("REJECT");
        return new ValidationResult(eligible, eligible ? "ELIGIBLE" : "NOT_ELIGIBLE",
                null, null, OffsetDateTime.now());
    }
}
