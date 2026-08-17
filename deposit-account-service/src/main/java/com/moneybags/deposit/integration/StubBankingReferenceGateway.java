package com.moneybags.deposit.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
                productId.replace('-', ' '), accountType,
                eligible ? List.of() : List.of("Customer or product does not meet the opening rules"),
                OffsetDateTime.now());
    }

    @Override
    public ValidationResult validateCustomerEligibility(String customerId) {
        CustomerProfile profile = customerProfile(customerId);
        return new ValidationResult(profile.eligible(), profile.eligible() ? "ELIGIBLE" : "NOT_ELIGIBLE",
                null, null, profile.eligible() ? List.of() : List.of("Customer KYC is not approved"),
                OffsetDateTime.now());
    }

    @Override
    public CustomerProfile customerProfile(String customerId) {
        boolean eligible = !customerId.toUpperCase().contains("REJECT");
        String category = customerId.toUpperCase().contains("SENIOR") ? "SENIOR_CITIZEN" : "REGULAR";
        int age = "SENIOR_CITIZEN".equals(category) ? 65 : 30;
        return new CustomerProfile(eligible, age, new BigDecimal("50000"),
                "INDIVIDUAL", category, eligible, OffsetDateTime.now());
    }
}
