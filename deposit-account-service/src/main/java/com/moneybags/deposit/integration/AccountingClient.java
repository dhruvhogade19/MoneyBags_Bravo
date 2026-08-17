package com.moneybags.deposit.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class AccountingClient {
    private final RestClient restClient;

    public AccountingClient(RestClient.Builder restClientBuilder,
                            @Value("${moneybags.clients.accounting.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
    }

    public AccountBalanceResponse getBalance(String accountReference) {
        return restClient.get().uri("/internal/v1/account-balances/{accountReference}", accountReference)
                .retrieve().body(AccountBalanceResponse.class);
    }

    public record AccountBalanceResponse(String accountReference, BigDecimal ledgerBalance, String currency,
                                         OffsetDateTime asOf) { }
}
