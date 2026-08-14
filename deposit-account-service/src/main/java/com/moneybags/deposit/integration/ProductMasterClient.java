package com.moneybags.deposit.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class ProductMasterClient {
    private final RestClient restClient;

    public ProductMasterClient(
            RestClient.Builder restClientBuilder,
            @Value("${moneybags.clients.product-master.base-url:http://product-master-service}") String baseUrl,
            @Value("${moneybags.clients.product-master.access-token:}") String accessToken) {
        RestClient.Builder builder = restClientBuilder.clone().baseUrl(baseUrl);
        if (accessToken != null && !accessToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        this.restClient = builder.build();
    }

    @CircuitBreaker(name = "productMaster")
    @Retry(name = "productMaster")
    public ProductResult getProduct(String productCode) {
        return restClient.get().uri("/api/v1/products/{productCode}", productCode)
                .retrieve().body(ProductResult.class);
    }

    @CircuitBreaker(name = "productMaster")
    @Retry(name = "productMaster")
    public AccountOpeningValidation validateAccountOpening(String productCode,
                                                            AccountOpeningValidationRequest request) {
        return restClient.post()
                .uri("/internal/v1/products/{productCode}/validate-account-opening", productCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AccountOpeningValidation.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductResult(String productCode, Long version, String productName, String category,
                         String subtype, String currencyCode, String accountType, String status,
                         InterestRule interestRule, AmountRule amountRule,
                         FixedDepositRule fixedDepositRule, List<InterestRateSlab> interestRateSlabs) {
        public boolean active() { return "ACTIVE".equalsIgnoreCase(status); }
        public String resolvedSubtype() { return subtype == null ? accountType : subtype; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterestRule(BigDecimal annualInterestRate, String policyVersion,
                        String interestCalculationMethod, String interestPostingFrequency,
                        String compoundingFrequency, String dayCountConvention) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmountRule(BigDecimal minimumOpeningBalance, BigDecimal minimumBalance,
                      BigDecimal maximumBalance, BigDecimal minimumAmount, BigDecimal maximumAmount,
                      Integer minimumTenureMonths, Integer maximumTenureMonths) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixedDepositRule(List<String> allowedTenureUnits,
                            List<String> allowedInterestPayoutFrequencies,
                            String defaultInterestPayoutFrequency, String compoundingFrequency,
                            String dayCountConvention) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterestRateSlab(String slabCode, Integer minimumTenure, Integer maximumTenure,
                            String tenureUnit, BigDecimal minimumAmount, BigDecimal maximumAmount,
                            String customerCategory, BigDecimal annualInterestRate,
                            LocalDate effectiveFrom, LocalDate effectiveTo, Boolean active) {}

    public record AccountOpeningValidationRequest(
            BigDecimal openingAmount, String currency, Integer age, String customerType,
            String customerCategory, Integer tenureMonths, String tenureUnit,
            String interestPayoutFrequency, Boolean kycVerified, Long productVersion,
            LocalDate valueDate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountOpeningValidation(
            boolean eligible, List<String> validationMessages, List<Object> applicableFees,
            InterestRule applicableInterestRule, AmountRule applicableAmountRules,
            String productCode, Long productVersion, Long appliedRuleVersion, String productName, String category,
            String subtype, String currencyCode, FixedDepositRule applicableFixedDepositRule,
            InterestRateSlab applicableInterestRateSlab) {
        public String rejectionMessage() {
            return validationMessages == null || validationMessages.isEmpty()
                    ? "Product validation rejected the request"
                    : String.join("; ", validationMessages);
        }
    }
}
