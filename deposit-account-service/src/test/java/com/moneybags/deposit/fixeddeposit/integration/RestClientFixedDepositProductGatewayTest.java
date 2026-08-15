package com.moneybags.deposit.fixeddeposit.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.domain.DomainTypes.CompoundingFrequency;
import com.moneybags.deposit.domain.DomainTypes.DayCountConvention;
import com.moneybags.deposit.domain.DomainTypes.InterestPayoutFrequency;
import com.moneybags.deposit.domain.DomainTypes.TenureUnit;
import com.moneybags.deposit.integration.ProductMasterClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestClientFixedDepositProductGatewayTest {

    @Test
    void usesTheResolvedProviderSlabAndSnapshotsTheDecision() {
        ProductMasterClient client = mock(ProductMasterClient.class);
        RestClientFixedDepositProductGateway gateway =
                new RestClientFixedDepositProductGateway(client, new ObjectMapper().findAndRegisterModules());
        LocalDate valueDate = LocalDate.of(2026, 8, 14);
        ProductMasterClient.AccountOpeningValidation decision =
                new ProductMasterClient.AccountOpeningValidation(true, List.of(), List.of(),
                        new ProductMasterClient.InterestRule(null, "V1", "COMPOUND_INTEREST",
                                "AT_MATURITY", "QUARTERLY", "ACTUAL_365"),
                        null, "FD-REG-001", 1L, 1L, "Regular Fixed Deposit", "DEPOSIT",
                        "FIXED_DEPOSIT", "INR",
                        new ProductMasterClient.FixedDepositRule(List.of("MONTH"),
                                List.of("AT_MATURITY"), "AT_MATURITY", "QUARTERLY", "ACTUAL_365"),
                        new ProductMasterClient.InterestRateSlab("FD-12M-TO-24M-SENIOR", 12, 24,
                                "MONTH", new BigDecimal("1000"), new BigDecimal("10000000"),
                                "SENIOR_CITIZEN", new BigDecimal("7.25"), valueDate, null, true));
        when(client.validateAccountOpening(org.mockito.ArgumentMatchers.eq("FD-REG-001"),
                org.mockito.ArgumentMatchers.any())).thenReturn(decision);

        FixedDepositProductGateway.ProductTerms terms = gateway.resolve("FD-REG-001", 1L,
                new BigDecimal("100000"), "INR", 12, TenureUnit.MONTH,
                InterestPayoutFrequency.AT_MATURITY, valueDate, 65, "INDIVIDUAL",
                "SENIOR_CITIZEN", true);

        assertThat(terms.annualRate()).isEqualByComparingTo("7.25");
        assertThat(terms.rateSlabCode()).isEqualTo("FD-12M-TO-24M-SENIOR");
        assertThat(terms.compoundingFrequency()).isEqualTo(CompoundingFrequency.QUARTERLY);
        assertThat(terms.dayCountConvention()).isEqualTo(DayCountConvention.ACTUAL_365);
        assertThat(terms.ruleSnapshotJson()).contains("FD-12M-TO-24M-SENIOR");

        ArgumentCaptor<ProductMasterClient.AccountOpeningValidationRequest> request =
                ArgumentCaptor.forClass(ProductMasterClient.AccountOpeningValidationRequest.class);
        verify(client).validateAccountOpening(org.mockito.ArgumentMatchers.eq("FD-REG-001"), request.capture());
        assertThat(request.getValue().customerCategory()).isEqualTo("SENIOR_CITIZEN");
        assertThat(request.getValue().kycVerified()).isTrue();
    }
}
