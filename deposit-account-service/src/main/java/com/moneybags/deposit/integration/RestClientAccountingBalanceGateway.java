package com.moneybags.deposit.integration;

import com.moneybags.deposit.exception.ApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class RestClientAccountingBalanceGateway implements AccountingBalanceGateway {
    private final AccountingClient client;

    public RestClientAccountingBalanceGateway(AccountingClient client) { this.client = client; }

    @Override
    @CircuitBreaker(name = "referenceServices")
    public AccountBalanceResult getBalance(String accountReference) {
        try {
            AccountingClient.AccountBalanceResponse value = client.getBalance(accountReference);
            return new AccountBalanceResult(value.accountReference(), value.ledgerBalance(), value.currency());
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Accounting balance is unavailable");
        }
    }
}
