package com.moneybags.deposit.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductMasterClientTest {

    @Test
    void callsVersionedInternalValidationContractAndMapsResolvedTerms() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProductMasterClient client = new ProductMasterClient(builder, "http://product-master.test", "service-token");

        server.expect(once(), requestTo("http://product-master.test/internal/v1/products/FD-REG-001/validate-account-opening"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andExpect(content().json("""
                        {"openingAmount":100000,"currency":"INR","age":30,"monthlyIncome":75000,"customerType":"INDIVIDUAL",
                         "customerCategory":"REGULAR","tenureMonths":12,"tenureUnit":"MONTH",
                         "interestPayoutFrequency":"AT_MATURITY","kycVerified":true,
                         "productVersion":1,"valueDate":"2026-08-14"}
                        """))
                .andRespond(withSuccess("""
                        {"eligible":true,"validationMessages":[],"productCode":"FD-REG-001",
                         "productVersion":1,"productName":"Regular Fixed Deposit","category":"DEPOSIT",
                         "subtype":"FIXED_DEPOSIT","currencyCode":"INR",
                         "applicableInterestRule":{"policyVersion":"V1","interestCalculationMethod":"COMPOUND_INTEREST"},
                         "applicableFixedDepositRule":{"compoundingFrequency":"QUARTERLY","dayCountConvention":"ACTUAL_365"},
                         "applicableInterestRateSlab":{"slabCode":"FD-12M-TO-24M-REGULAR",
                           "annualInterestRate":6.75,"active":true}}
                        """, MediaType.APPLICATION_JSON));

        ProductMasterClient.AccountOpeningValidation decision = client.validateAccountOpening("FD-REG-001",
                new ProductMasterClient.AccountOpeningValidationRequest(new BigDecimal("100000"), "INR", 30,
                        new BigDecimal("75000"), "INDIVIDUAL", "REGULAR", 12, "MONTH", "AT_MATURITY", true, 1L,
                        LocalDate.of(2026, 8, 14)));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.subtype()).isEqualTo("FIXED_DEPOSIT");
        assertThat(decision.applicableInterestRateSlab().slabCode())
                .isEqualTo("FD-12M-TO-24M-REGULAR");
        assertThat(decision.applicableInterestRateSlab().annualInterestRate())
                .isEqualByComparingTo("6.75");
        server.verify();
    }
}
