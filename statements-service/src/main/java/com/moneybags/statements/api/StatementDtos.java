package com.moneybags.statements.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class StatementDtos {
    private StatementDtos() {}

    public record GenerateStatementRequest(@NotBlank String cifId,
            @NotBlank String accountReference, @NotBlank String accountType,
            @NotBlank String maskedAccountReference, @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd) {}

    public record GenerateAccountStatementRequest(@NotBlank String accountReference,
            @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}

    public record StatementLineView(int sequence, String transactionId, String paymentId,
            OffsetDateTime occurredAt, String description, BigDecimal debit, BigDecimal credit,
            BigDecimal balanceAfter, String journalNumber) {}

    public record AccountActivityView(String accountReference, String accountType,
            String maskedAccountReference, LocalDate periodStart, LocalDate periodEnd,
            String currency, BigDecimal openingBalance, BigDecimal closingBalance,
            BigDecimal totalDebits, BigDecimal totalCredits, boolean reconciled,
            List<StatementLineView> lines) {}

    public record StatementView(String statementId, String accountReference, String accountType,
            String maskedAccountReference, LocalDate periodStart, LocalDate periodEnd, String currency,
            BigDecimal openingBalance, BigDecimal closingBalance, OffsetDateTime generatedAt,
            String status, List<StatementLineView> lines) {}
}
