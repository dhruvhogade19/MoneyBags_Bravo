package com.moneybags.statements.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface StatementSourceGateway {
    AccountContext context(String accountReference);
    StatementSource load(String accountReference, LocalDate periodStart, LocalDate periodEnd);

    record AccountContext(String accountId, String maskedAccountReference, String accountType,
                          String currency, List<String> customerIds) {}
    record StatementSource(List<LedgerEntry> ledgerEntries, List<DepositActivity> depositActivities,
                           BigDecimal openingBalance, BigDecimal closingBalance, String currency) {}
    record LedgerEntry(String journalNumber, LocalDate businessDate, OffsetDateTime occurredAt,
                       String eventType, BigDecimal debitAmount, BigDecimal creditAmount,
                       String currencyCode, String narration) {}
    record DepositActivity(String transactionId, String paymentId, String direction,
                           BigDecimal amount, String currency, BigDecimal balanceBefore,
                           BigDecimal balanceAfter, OffsetDateTime createdAt) {}
}
