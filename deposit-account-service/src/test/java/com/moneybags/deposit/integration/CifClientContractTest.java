package com.moneybags.deposit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CifClientContractTest {
    @Test
    void mapsTheCifDepositProjectionContract() {
        var details = new CifClient.DepositCreationDetails(
                "101", LocalDate.of(1990, 1, 15), "SALARIED", "APPROVED");

        assertThat(details.dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(details.customerType()).isEqualTo("INDIVIDUAL");
        assertThat(details.kycCompleted()).isTrue();
    }
}
