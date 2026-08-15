package com.moneybags.creditcard.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "moneybags.credit-card.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubCreditCardReferenceGateway implements CreditCardReferenceGateway {
    public CifDetails getCreditCardDetails(Long id) {
        return new CifDetails(id, "BUSINESS", new BigDecimal("100000"), 30, "APPROVED");
    }

    public ProductValidation validateApplication(String code, BigDecimal limit, CifDetails c) {
        boolean ok = "APPROVED".equalsIgnoreCase(c.kycStatus()) && !code.toUpperCase().contains("REJECT");
        return new ProductValidation(ok, new ApplicableInterestRule(new BigDecimal("18.0000")));
    }
}
