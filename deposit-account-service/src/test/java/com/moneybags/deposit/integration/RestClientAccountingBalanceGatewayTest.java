package com.moneybags.deposit.integration;

import com.moneybags.deposit.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestClientAccountingBalanceGatewayTest {

    @Test
    void mapsTheAccountingServiceResponseToTheDepositGatewayContract() {
        AccountingClient client = mock(AccountingClient.class);
        when(client.getBalance("DEP-123")).thenReturn(new AccountingClient.AccountBalanceResponse(
                "DEP-123", new BigDecimal("1250.5000"), "INR", OffsetDateTime.parse("2026-08-17T10:00:00Z")));
        RestClientAccountingBalanceGateway gateway = new RestClientAccountingBalanceGateway(client);

        AccountingBalanceGateway.AccountBalanceResult result = gateway.getBalance("DEP-123");

        assertThat(result.accountReference()).isEqualTo("DEP-123");
        assertThat(result.ledgerBalance()).isEqualByComparingTo("1250.5000");
        assertThat(result.currency()).isEqualTo("INR");
    }

    @Test
    void translatesAnAccountingTransportFailureToServiceUnavailable() {
        AccountingClient client = mock(AccountingClient.class);
        when(client.getBalance("DEP-123")).thenThrow(new RestClientException("connection refused"));
        RestClientAccountingBalanceGateway gateway = new RestClientAccountingBalanceGateway(client);

        assertThatThrownBy(() -> gateway.getBalance("DEP-123"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
                    assertThat(exception.getMessage()).isEqualTo("Accounting balance is unavailable");
                });
    }
}
