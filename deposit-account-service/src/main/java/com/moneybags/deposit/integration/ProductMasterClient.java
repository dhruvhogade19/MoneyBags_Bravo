package com.moneybags.deposit.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class ProductMasterClient {
    private final RestClient restClient;

    public ProductMasterClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone().baseUrl("http://product-master-service/api/products").build();
    }

    public ProductResult getProduct(String productCode) {
        return restClient.get().uri("/{productCode}", productCode).retrieve().body(ProductResult.class);
    }

    public AccountOpeningValidation validateAccountOpening(String productCode, AccountOpeningValidationRequest request) {
        return restClient.post().uri("/{productCode}/validate-account-opening", productCode)
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(AccountOpeningValidation.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductResult(String productCode, Long version, String productName, String category,
                         String subtype, String currencyCode, String accountType, String status,
                         InterestRule interestRule, AmountRule amountRule,
                         FixedDepositRule fixedDepositRule, List<InterestRateSlab> interestRateSlabs) {
        public boolean active() { return "ACTIVE".equalsIgnoreCase(status); }
        public String resolvedSubtype() { return subtype == null ? accountType : subtype; }
        public boolean supportedDepositType() {
            return "SAVINGS".equalsIgnoreCase(resolvedSubtype()) || "CURRENT".equalsIgnoreCase(resolvedSubtype())
                    || "FIXED_DEPOSIT".equalsIgnoreCase(resolvedSubtype());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterestRule(BigDecimal annualInterestRate, String policyVersion, String interestCalculationMethod,
                        String interestPostingFrequency, String compoundingFrequency, String dayCountConvention) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmountRule(BigDecimal minimumAmount, BigDecimal maximumAmount,
                      Integer minimumTenureMonths, Integer maximumTenureMonths) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixedDepositRule(List<String> allowedTenureUnits, List<String> allowedInterestPayoutFrequencies,
                            String defaultInterestPayoutFrequency, String compoundingFrequency,
                            String dayCountConvention) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterestRateSlab(String slabCode, Integer minimumTenure, Integer maximumTenure, String tenureUnit,
                            BigDecimal minimumAmount, BigDecimal maximumAmount, String customerCategory,
                            BigDecimal annualInterestRate, LocalDate effectiveFrom, LocalDate effectiveTo,
                            Boolean active) {}

    public record AccountOpeningValidationRequest(BigDecimal openingAmount, int age, String customerType,
                                           boolean kycCompleted) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountOpeningValidation(boolean eligible) {}
}
