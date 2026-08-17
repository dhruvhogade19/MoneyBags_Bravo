package com.moneybags.deposit.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface BankingReferenceGateway {
    ValidationResult validateAccountOpening(String customerId, String productCode, Long productVersion,
                                            String currency, BigDecimal openingAmount);

    ValidationResult validateCustomerEligibility(String customerId);

    CustomerProfile customerProfile(String customerId);

    record ValidationResult(boolean eligible, String decisionCode, String productName, String accountType,
                            List<String> messages, OffsetDateTime evaluatedAt) {}

    record CustomerProfile(boolean eligible, Integer age, BigDecimal monthlyIncome,
                           String customerType, String customerCategory,
                           boolean kycVerified, OffsetDateTime evaluatedAt) {}
}
