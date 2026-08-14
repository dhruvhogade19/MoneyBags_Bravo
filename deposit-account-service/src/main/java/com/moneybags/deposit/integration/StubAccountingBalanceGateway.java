package com.moneybags.deposit.integration;

import com.moneybags.deposit.entity.AccountBalance;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.repository.AccountBalanceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubAccountingBalanceGateway implements AccountingBalanceGateway {
    private final AccountBalanceRepository balances;

    public StubAccountingBalanceGateway(AccountBalanceRepository balances) { this.balances = balances; }

    @Override
    public AccountBalanceResult getBalance(String accountReference) {
        AccountBalance value = balances.findById(accountReference).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_BALANCE_NOT_FOUND", "Accounting balance was not found"));
        return new AccountBalanceResult(accountReference, value.getLedgerBalance(), value.getCurrencyCode());
    }
}
