package com.moneybags.deposit.closure.dto;

import com.moneybags.deposit.domain.DomainTypes.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class AccountClosureResponses {
    private AccountClosureResponses() {}
    public record ClosureCheckView(String code,ClosureCheckStatus status,String details,OffsetDateTime checkedAt) {}
    public record ClosureQuoteResponse(boolean eligible,String accountId,ProductSubtype accountSubtype,
        BigDecimal currentBalance,BigDecimal closureFee,BigDecimal netSettlementAmount,String currency,
        String destinationAccountId,List<String> blockers,OffsetDateTime quoteValidUntil) {}
    public record ClosureSettlementView(BigDecimal principalAmount,BigDecimal originalInterestAmount,
        BigDecimal recalculatedInterestAmount,BigDecimal interestPenaltyAmount,BigDecimal closureFeeAmount,
        BigDecimal taxAmount,BigDecimal netPayoutAmount,String currency,String destinationAccountId,
        String transactionReference,ClosureSettlementStatus status) {}
    public record ClosureRequestView(String closureRequestId,String accountId,ClosureType closureType,
        ClosureRequestStatus status,String requestedBy,String requestedChannel,LocalDate requestedDate,
        String reasonCode,String reasonText,String destinationAccountId,String rejectionCode,
        String rejectionDetails,String policyVersion,List<ClosureCheckView> checks,
        ClosureSettlementView settlement,OffsetDateTime createdAt,OffsetDateTime completedAt,long version) {}
    public record PrematureClosureQuoteResponse(boolean eligible,String fixedDepositId,BigDecimal principal,
        BigDecimal bookedAnnualRate,long completedHoldingDays,BigDecimal applicableAnnualRate,
        BigDecimal penaltyRate,BigDecimal finalAnnualRate,BigDecimal originalExpectedInterest,
        BigDecimal recalculatedInterest,BigDecimal interestRecoveryAmount,BigDecimal netPayoutAmount,
        String currency,String destinationAccountId,List<String> blockers,OffsetDateTime quoteValidUntil) {}
}
