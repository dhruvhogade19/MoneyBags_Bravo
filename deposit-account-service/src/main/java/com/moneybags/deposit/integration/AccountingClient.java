package com.moneybags.deposit.integration;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class AccountingClient {
    private final RestClient restClient;

    public AccountingClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone().baseUrl("http://accounting-service/internal/v1/account-balances").build();
    }

    public AccountBalanceResponse getBalance(String accountReference) {
        return restClient.get().uri("/{accountReference}", accountReference).retrieve().body(AccountBalanceResponse.class);
    }

    public record AccountBalanceResponse(String accountReference, BigDecimal ledgerBalance, String currency, OffsetDateTime asOf) {}
}
