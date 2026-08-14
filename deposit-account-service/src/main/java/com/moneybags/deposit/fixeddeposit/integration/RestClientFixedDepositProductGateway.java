package com.moneybags.deposit.fixeddeposit.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.domain.DomainTypes.CompoundingFrequency;
import com.moneybags.deposit.domain.DomainTypes.DayCountConvention;
import com.moneybags.deposit.domain.DomainTypes.InterestPayoutFrequency;
import com.moneybags.deposit.domain.DomainTypes.TenureUnit;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.integration.ProductMasterClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class RestClientFixedDepositProductGateway implements FixedDepositProductGateway {
    private final ProductMasterClient client;
    private final ObjectMapper mapper;

    public RestClientFixedDepositProductGateway(ProductMasterClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public ProductTerms resolve(String code, Long version, BigDecimal principal, String currency,
                                int tenure, TenureUnit unit, InterestPayoutFrequency payout,
                                LocalDate valueDate, Integer customerAge, String customerType,
                                String customerCategory, boolean kycVerified) {
        try {
            ProductMasterClient.AccountOpeningValidation decision = client.validateAccountOpening(code,
                    new ProductMasterClient.AccountOpeningValidationRequest(principal, currency, customerAge,
                            customerType, customerCategory, tenure, unit.name(), payout.name(), kycVerified,
                            version, valueDate));
            if (decision == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PRODUCT_MASTER_RESPONSE",
                        "Product Master returned an empty validation response");
            }
            if (decision.productCode() == null || decision.productVersion() == null
                    || decision.productName() == null || decision.category() == null
                    || decision.subtype() == null || decision.currencyCode() == null
                    || !code.equalsIgnoreCase(decision.productCode())
                    || !version.equals(decision.productVersion())
                    || !currency.equalsIgnoreCase(decision.currencyCode())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PRODUCT_MASTER_RESPONSE",
                        "Product Master returned inconsistent product identity data");
            }
            if (!decision.eligible()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FIXED_DEPOSIT_PRODUCT_REJECTED",
                        decision.rejectionMessage());
            }
            if (!"DEPOSIT".equalsIgnoreCase(decision.category())
                    || !"FIXED_DEPOSIT".equalsIgnoreCase(decision.subtype())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_ACTIVE_FIXED_DEPOSIT",
                        "Product is not an active fixed deposit");
            }
            ProductMasterClient.InterestRateSlab slab = decision.applicableInterestRateSlab();
            if (slab == null || slab.annualInterestRate() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "FD_RATE_CONFIGURATION_INVALID",
                        "Product Master did not resolve exactly one fixed-deposit rate slab");
            }
            ProductMasterClient.InterestRule interest = decision.applicableInterestRule();
            ProductMasterClient.FixedDepositRule fixedDeposit = decision.applicableFixedDepositRule();
            String compounding = fixedDeposit == null || fixedDeposit.compoundingFrequency() == null
                    ? "QUARTERLY" : fixedDeposit.compoundingFrequency();
            String dayCount = fixedDeposit == null || fixedDeposit.dayCountConvention() == null
                    ? "ACTUAL_365" : fixedDeposit.dayCountConvention();
            String calculation = interest == null || interest.interestCalculationMethod() == null
                    ? "COMPOUND_INTEREST" : interest.interestCalculationMethod();
            return new ProductTerms(decision.productCode(), decision.productVersion(), decision.productName(),
                    slab.slabCode(), interest == null ? null : interest.policyVersion(), slab.annualInterestRate(),
                    calculation, CompoundingFrequency.valueOf(compounding), payout,
                    DayCountConvention.valueOf(dayCount), mapper.writeValueAsString(decision));
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_VALIDATION_FAILED",
                    "Product Master rejected the fixed-deposit request");
        } catch (CallNotPermittedException | RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_MASTER_UNAVAILABLE",
                    "Product Master is unavailable");
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PRODUCT_MASTER_RESPONSE",
                    "Product Master returned an invalid fixed-deposit configuration");
        }
    }
}
