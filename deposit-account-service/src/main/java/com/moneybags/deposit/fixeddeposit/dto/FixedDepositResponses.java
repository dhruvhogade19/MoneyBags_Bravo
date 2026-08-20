package com.moneybags.deposit.fixeddeposit.dto;

import com.moneybags.deposit.domain.DomainTypes.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class FixedDepositResponses {
    private FixedDepositResponses() {}
    public record QuoteResponse(String productCode, Long productVersion, String productName, String rateSlabCode,
        BigDecimal annualInterestRate, BigDecimal principal, LocalDate valueDate, LocalDate maturityDate,
        BigDecimal expectedInterest, BigDecimal expectedMaturityAmount, String calculationMethod,
        CompoundingFrequency compoundingFrequency, DayCountConvention dayCountConvention) {}
    public record FixedDepositView(String fixedDepositId, String accountId, String maskedAccountNumber,
        String productCode, Long productVersion, FixedDepositStatus status, BigDecimal principal, String currency,
        BigDecimal annualInterestRate, LocalDate valueDate, LocalDate maturityDate, BigDecimal expectedInterest,
        BigDecimal expectedMaturityAmount, BigDecimal accruedInterest, String fundingAccountId,
        String payoutAccountId, long version) {}
    public record AccrualView(LocalDate businessDate, BigDecimal accrualBase, BigDecimal annualRate,
        BigDecimal interestAmount, BigDecimal cumulativeInterest, String status, OffsetDateTime createdAt) {}
    public record ProjectedScheduleResponse(String fixedDepositId, LocalDate valueDate, LocalDate maturityDate,
        BigDecimal principal, BigDecimal annualInterestRate, BigDecimal projectedInterest,
        BigDecimal projectedMaturityAmount, InterestPayoutFrequency payoutFrequency) {}
    /**
     * {@code processed}/{@code totalAmount} retain the original Deposit-effect contract.  The Accounting
     * fields deliberately describe only journals owned by this EOD correlation, so reconciliation never
     * attributes a journal created by an older run to the current run.
     */
    public record EodResult(String eodRunId, LocalDate businessDate, String commandReference,
        int processed, int skipped, BigDecimal totalAmount, List<String> failures,
        long postedJournalCount, BigDecimal postedDebitTotal) {}
    public record ReadinessResponse(boolean ready, long pendingFunding, long pendingPayouts, List<String> blockers) {}
}
