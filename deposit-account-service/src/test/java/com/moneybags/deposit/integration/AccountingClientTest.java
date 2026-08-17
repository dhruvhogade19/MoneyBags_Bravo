package com.moneybags.deposit.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountingClientTest {

    @Test
    void callsBalanceEndpointUsingConfiguredAccountingBaseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AccountingClient client = new AccountingClient(builder, "http://localhost:8088");

        server.expect(once(), requestTo("http://localhost:8088/internal/v1/account-balances/DEP-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"accountReference":"DEP-123","ledgerBalance":1250.50,"currency":"INR",
                         "asOf":"2026-08-17T10:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        AccountingClient.AccountBalanceResponse balance = client.getBalance("DEP-123");

        assertThat(balance.accountReference()).isEqualTo("DEP-123");
        assertThat(balance.ledgerBalance()).isEqualByComparingTo("1250.50");
        assertThat(balance.currency()).isEqualTo("INR");
        assertThat(balance.asOf()).isEqualTo("2026-08-17T10:00:00Z");
        server.verify();
    }
}
