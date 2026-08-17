package com.moneybags.deposit.integration;

import com.moneybags.deposit.exception.ApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class RestClientBankingReferenceGateway implements BankingReferenceGateway {
    private final CifClient cifClient;
    private final ProductMasterClient productClient;

    public RestClientBankingReferenceGateway(CifClient cifClient, ProductMasterClient productClient) {
        this.cifClient = cifClient;
        this.productClient = productClient;
    }

    @Override
    public ValidationResult validateAccountOpening(String customerId, String productCode, Long productVersion,
                                                   String currency, BigDecimal openingAmount) {
        CustomerProfile customer = customerProfile(customerId);
        try {
            ProductMasterClient.AccountOpeningValidation decision = productClient.validateAccountOpening(productCode,
                    new ProductMasterClient.AccountOpeningValidationRequest(openingAmount, currency,
                            customer.age(), customer.customerType(), customer.customerCategory(), null,
                            null, null, customer.kycVerified(), productVersion, LocalDate.now()));
            if (decision == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PRODUCT_MASTER_RESPONSE",
                        "Product Master returned an empty validation response");
            }
            if (decision.productCode() == null || decision.productVersion() == null
                    || decision.productName() == null || decision.subtype() == null
                    || decision.currencyCode() == null
                    || !productCode.equalsIgnoreCase(decision.productCode())
                    || !productVersion.equals(decision.productVersion())
                    || !currency.equalsIgnoreCase(decision.currencyCode())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PRODUCT_MASTER_RESPONSE",
                        "Product Master returned inconsistent product identity data");
            }
            boolean supported = "SAVINGS".equalsIgnoreCase(decision.subtype())
                    || "CURRENT".equalsIgnoreCase(decision.subtype());
            boolean eligible = customer.eligible() && decision.eligible() && supported;
            String code = eligible ? "ELIGIBLE"
                    : !customer.eligible() ? "KYC_APPROVAL_REQUIRED"
                    : !supported ? "UNSUPPORTED_ACCOUNT_TYPE"
                    : "PRODUCT_ELIGIBILITY_FAILED";
            return new ValidationResult(eligible, code, decision.productName(), decision.subtype(),
                    OffsetDateTime.now());
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_VALIDATION_FAILED",
                    "Product Master rejected the account-opening request");
        } catch (CallNotPermittedException | RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_MASTER_UNAVAILABLE",
                    "Product Master is unavailable");
        }
    }

    @Override
    public ValidationResult validateCustomerEligibility(String customerId) {
        CustomerProfile customer = customerProfile(customerId);
        return new ValidationResult(customer.eligible(), customer.eligible()
                ? "ELIGIBLE" : "CUSTOMER_OR_KYC_NOT_ELIGIBLE", null, null, customer.evaluatedAt());
    }

    @Override
    public CustomerProfile customerProfile(String customerId) {
        try {
            CifClient.DepositCreationDetails customer = cifClient.depositCreationDetails(customerId);
            int age = age(customer.dateOfBirth());
            boolean kycVerified = customer.kycCompleted();
            String category = customer.customerCategory();
            if (category == null || category.isBlank()) {
                category = age >= 60 ? "SENIOR_CITIZEN" : "REGULAR";
            }
            return new CustomerProfile(kycVerified, age, customer.customerType(), category,
                    kycVerified, OffsetDateTime.now());
        } catch (CallNotPermittedException | RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CIF_UNAVAILABLE",
                    "CIF status is unavailable");
        }
    }

    private int age(LocalDate dateOfBirth) {
        return dateOfBirth == null ? 0 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
