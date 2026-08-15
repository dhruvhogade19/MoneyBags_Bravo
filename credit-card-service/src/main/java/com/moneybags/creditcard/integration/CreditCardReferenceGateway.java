package com.moneybags.creditcard.integration;

import java.math.BigDecimal;

public interface CreditCardReferenceGateway {
    CifDetails getCreditCardDetails(Long cifId);

    ProductValidation validateApplication(String productCode, BigDecimal requestedCreditLimit, CifDetails customer);

    record CifDetails(Long cifId, String employmentType, BigDecimal salary, Integer age, String kycStatus) {
    }

    record ProductValidation(boolean eligible, ApplicableInterestRule applicableInterestRule) {
    }

    record ApplicableInterestRule(BigDecimal annualInterestRate) {
    }
}
