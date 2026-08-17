package com.moneybags.deposit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CifClientContractTest {
    @Test
    void mapsTheCifDepositProjectionContract() {
        var details = new CifClient.DepositCreationDetails(
                "101", LocalDate.of(1990, 1, 15), "SALARIED", new BigDecimal("75000"), "APPROVED");

        assertThat(details.dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(details.customerType()).isEqualTo("INDIVIDUAL");
        assertThat(details.monthlyIncome()).isEqualByComparingTo("75000");
        assertThat(details.kycCompleted()).isTrue();
    }

    @Test
    void mapsBusinessEmploymentToBusinessCustomerType() {
        var details = new CifClient.DepositCreationDetails(
                "102", LocalDate.of(1992, 6, 10), "BUSINESS", new BigDecimal("125000"), "APPROVED");

        assertThat(details.customerType()).isEqualTo("BUSINESS");
        assertThat(details.kycCompleted()).isTrue();
    }
}
