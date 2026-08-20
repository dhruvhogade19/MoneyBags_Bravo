package com.moneybags.deposit.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Contract between Deposit Account Service and Accounting's typed Fixed Deposit posting API. */
public interface AccountingFixedDepositPostingGateway {
    PostingResponse post(FixedDepositPosting request, String idempotencyKey, String correlationId);

    record FixedDepositPosting(
            String postingReference,
            String postingType,
            String fixedDepositAccountId,
            String productCode,
            String currencyCode,
            LocalDate businessDate,
            OffsetDateTime occurredAt,
            List<PostingComponent> components,
            String fundingAccountId,
            String payoutAccountId,
            String payoutMode,
            String reasonCode,
            String narration) { }

    record PostingComponent(String componentType, BigDecimal amount) { }

    record PostingResponse(String journalNumber, String status, BigDecimal totalDebit,
                           boolean idempotentReplay, String correlationId) { }
}
