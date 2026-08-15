package com.moneybags.deposit.integration;

import java.math.BigDecimal;

public interface AccountingBalanceGateway {
    AccountBalanceResult getBalance(String accountReference);

    record AccountBalanceResult(String accountReference, BigDecimal ledgerBalance, String currency) {}
}
