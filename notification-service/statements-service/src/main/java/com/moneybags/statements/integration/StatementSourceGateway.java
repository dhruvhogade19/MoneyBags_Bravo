package com.moneybags.statements.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface StatementSourceGateway {
    StatementSource load(String accountReference, LocalDate periodStart, LocalDate periodEnd);
    record StatementSource(List<LedgerEntry> ledgerEntries, List<DepositActivity> depositActivities) {}
    record LedgerEntry(String journalNumber, LocalDate businessDate, OffsetDateTime occurredAt, String eventType,
            BigDecimal debitAmount, BigDecimal creditAmount, String currencyCode, String narration) {}
    record DepositActivity(String transactionId, String paymentId, String direction, BigDecimal amount,
            String currency, BigDecimal balanceBefore, BigDecimal balanceAfter, OffsetDateTime createdAt) {}
}
