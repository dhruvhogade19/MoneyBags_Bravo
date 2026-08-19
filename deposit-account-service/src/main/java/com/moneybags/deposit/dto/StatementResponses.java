package com.moneybags.deposit.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class StatementResponses {
    private StatementResponses() {}

    public record StatementAccountContext(
            String accountId,
            String maskedAccountReference,
            String accountType,
            String currency,
            List<String> customerIds) {}

    public record StatementActivity(
            String transactionId,
            String paymentId,
            String direction,
            BigDecimal amount,
            String currency,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            OffsetDateTime createdAt) {}

    public record StatementActivityPage(
            List<StatementActivity> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            String currency) {}
}
