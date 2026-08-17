package com.moneybags.deposit.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface BankingReferenceGateway {
    ValidationResult validateAccountOpening(String customerId, String productCode, Long productVersion,
                                            String currency, BigDecimal openingAmount);

    ValidationResult validateCustomerEligibility(String customerId);

    record ValidationResult(boolean eligible, String decisionCode, String productName, String accountType,
                            OffsetDateTime evaluatedAt) {}
}
