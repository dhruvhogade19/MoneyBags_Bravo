package com.moneybags.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EodResponses {
    private EodResponses() {}

    public record DepositAccrualResponse(String eodRunId, String commandReference, LocalDate businessDate,
                                         int processedCount, int failedCount, BigDecimal totalAmount,
                                         List<String> failures) {}

    public record ServiceReadinessResponse(String service, LocalDate businessDate, boolean ready,
                                           List<String> blockers) {}
}
