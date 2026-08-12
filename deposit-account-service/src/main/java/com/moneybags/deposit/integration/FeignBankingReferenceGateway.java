package com.moneybags.deposit.integration;

import com.moneybags.deposit.exception.ApiException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class FeignBankingReferenceGateway implements BankingReferenceGateway {
    private final CifClient cifClient;
    private final ProductMasterClient productClient;

    public FeignBankingReferenceGateway(CifClient cifClient, ProductMasterClient productClient) {
        this.cifClient = cifClient;
        this.productClient = productClient;
    }

    @Override
    @CircuitBreaker(name = "referenceServices")
    public ValidationResult validateAccountOpening(String customerId, String productCode, Long productVersion,
                                                   String currency, BigDecimal openingAmount) {
        try {
            CifClient.DepositCreationDetails customer = cifClient.depositCreationDetails(customerId);
            ProductMasterClient.ProductResult product = productClient.getProduct(productCode);
            ProductMasterClient.AccountOpeningValidation rules = productClient.validateAccountOpening(productCode,
                    new ProductMasterClient.AccountOpeningValidationRequest(openingAmount,
                            age(customer.dateOfBirth()), customer.customerType(), customer.kycCompleted()));
            boolean versionMatches = product.version() == null || product.version().equals(productVersion);
            boolean eligible = customer.active() && customer.kycCompleted() && product.active()
                    && product.supportedDepositType() && versionMatches
                    && currency.equals(product.currencyCode()) && rules != null && rules.eligible();
            String code = eligible ? "ELIGIBLE" : product.supportedDepositType()
                    ? "REFERENCE_VALIDATION_FAILED" : "UNSUPPORTED_ACCOUNT_TYPE";
            return new ValidationResult(eligible, code, product.productName(), product.accountType(), OffsetDateTime.now());
        } catch (FeignException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "CIF or Product Master is unavailable");
        }
    }

    @Override
    @CircuitBreaker(name = "referenceServices")
    public ValidationResult validateCustomerEligibility(String customerId) {
        try {
            CifClient.DepositCreationDetails customer = cifClient.depositCreationDetails(customerId);
            boolean eligible = customer.active() && customer.kycCompleted();
            return new ValidationResult(eligible, eligible ? "ELIGIBLE" : "CUSTOMER_OR_KYC_NOT_ELIGIBLE",
                    null, null, OffsetDateTime.now());
        } catch (FeignException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "CIF status is unavailable");
        }
    }

    private int age(LocalDate dateOfBirth) {
        return dateOfBirth == null ? 0 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
