package com.moneybags.accounting.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.moneybags.accounting.domain.DomainTypes.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class AccountingDtos {
    private AccountingDtos() {}

    public record InstrumentReference(
            @NotBlank @Size(max = 40) String instrumentType,
            @Size(max = 100) String accountId,
            @Size(max = 100) String merchantId) {
        public String reference() { return accountId != null && !accountId.isBlank() ? accountId : merchantId; }
    }

    public record PaymentSettlementPostingRequest(
            @NotBlank @Size(max = 100) String paymentId,
            @NotBlank @Size(max = 60) String paymentType,
            @NotNull @Valid InstrumentReference source,
            @NotNull @Valid InstrumentReference destination,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull OffsetDateTime occurredAt,
            @NotNull LocalDate businessDate,
            @Size(max = 255) String reference) {}

    public record PaymentRefundPostingRequest(
            @NotBlank @Size(max = 100) String refundId,
            @NotBlank @Size(max = 100) String paymentId,
            @NotBlank @Size(max = 60) String originalJournalNumber,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull OffsetDateTime occurredAt,
            @NotNull LocalDate businessDate,
            @NotBlank @Size(max = 500) String reason) {}

    public record JournalReversalRequest(
            @Size(max = 100) String paymentId,
            @NotNull LocalDate businessDate,
            @NotNull OffsetDateTime occurredAt,
            @NotBlank @Size(max = 500) String reason) {}

    public record BillComponent(
            @NotBlank @Size(max = 40) String componentType,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 500) String description) {}

    public record BillAccountingPostingRequest(
            @NotBlank @Size(max = 100) String billId,
            @NotBlank @Size(max = 100) String accountId,
            @Size(max = 40) String productCode,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd,
            @NotNull LocalDate businessDate,
            @NotNull OffsetDateTime occurredAt,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotEmpty List<@Valid BillComponent> components) {}

    public record FixedDepositComponent(
            @NotBlank @Size(max = 40) String componentType,
            @NotNull @DecimalMin(value = "0.0000", inclusive = true) @Digits(integer = 15, fraction = 4)
            BigDecimal amount) {}

    public record FixedDepositPostingRequest(
            @NotBlank @Size(max = 160) String postingReference,
            @Size(max = 40) String postingType,
            @NotBlank @Size(max = 100) String fixedDepositAccountId,
            @Size(max = 40) String productCode,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull LocalDate businessDate,
            @NotNull OffsetDateTime occurredAt,
            @NotEmpty List<@Valid FixedDepositComponent> components,
            @Size(max = 100) String fundingAccountId,
            @Size(max = 100) String payoutAccountId,
            @Size(max = 30) String payoutMode,
            @Size(max = 40) String reasonCode,
            @Size(max = 500) String narration) {}

    public record JournalLineResponse(
            int lineNumber,
            String glCode,
            String subledgerReference,
            String componentType,
            String ruleCode,
            int ruleVersion,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String narration) {}

    public record JournalResponse(
            String journalNumber,
            long postingSequence,
            String externalReference,
            String sourceService,
            String eventType,
            OffsetDateTime occurredAt,
            LocalDate businessDate,
            String currencyCode,
            String status,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String correlationId,
            OffsetDateTime postedAt,
            boolean idempotentReplay,
            String reversesJournalNumber,
            List<JournalLineResponse> lines) {}

    public record PostingOutcomeResponse(
            String externalReference,
            String status,
            String journalNumber,
            OffsetDateTime receivedAt,
            OffsetDateTime completedAt,
            JournalResponse journal) {}

    public record JournalPage(List<JournalResponse> content, int page, int size, long totalElements,
                              int totalPages) {}

    public record AccountBalanceResponse(
            String accountReference,
            BigDecimal ledgerBalance,
            String currency,
            OffsetDateTime asOf) {}

    public record LedgerEntryResponse(
            String journalNumber,
            int lineNumber,
            LocalDate businessDate,
            OffsetDateTime occurredAt,
            String eventType,
            String glCode,
            String subledgerReference,
            String componentType,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currencyCode,
            String narration) {}

    public record LedgerEntryPage(List<LedgerEntryResponse> content, int page, int size, long totalElements,
                                  int totalPages) {}

    public record AccountLifecycleEventRequest(
            @NotBlank @Size(max = 160) String eventReference,
            @NotNull LifecycleEventType eventType,
            @NotNull AccountType accountType,
            @NotBlank @Size(max = 100) String accountReference,
            @Size(max = 40) String productCode,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull LocalDate businessDate,
            @NotNull OffsetDateTime occurredAt,
            @Size(max = 40) String reasonCode) {}

    public record AccountLifecycleResponse(
            String eventReference,
            AccountType accountType,
            String accountReference,
            LifecycleState accountingLifecycleState,
            OffsetDateTime processedAt,
            String correlationId,
            boolean idempotentReplay) {}

    public record ClearanceBalance(
            String currencyCode,
            String logicalRole,
            BigDecimal amount) {}

    public record AccountClearanceResponse(
            AccountType accountType,
            String accountReference,
            boolean accountingCleared,
            List<ClearanceBalance> balances,
            List<String> blockers,
            long lastPostingSequence,
            OffsetDateTime checkedAt) {}

    public record GlAccountRequest(
            @NotBlank @Size(max = 40) String glCode,
            @NotBlank @Size(max = 160) String name,
            @NotNull GlAccountType accountType,
            @NotNull NormalBalance normalBalance,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @Size(max = 40) String parentGlCode) {}

    public record StatusChangeRequest(@NotNull RecordStatus status) {}

    public record GlAccountResponse(String glCode, String name, GlAccountType accountType,
                                    NormalBalance normalBalance, String currencyCode, String parentGlCode,
                                    RecordStatus status, long version) {}
    public record GlAccountPage(List<GlAccountResponse> content, int page, int size, long totalElements,
                                int totalPages) {}

    public record AccountingRuleRequest(
            @NotBlank @Size(max = 60) String ruleCode,
            @NotBlank @Size(max = 60) String eventType,
            @NotBlank @Size(max = 40) String componentType,
            @Size(max = 40) String productCode,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @Min(1) int version,
            @NotBlank @Size(max = 60) String debitMappingCode,
            @NotBlank @Size(max = 60) String creditMappingCode,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record AccountingRuleResponse(String ruleCode, String eventType, String componentType,
                                         String productCode, String currencyCode, int version,
                                         String debitMappingCode, String creditMappingCode,
                                         LocalDate effectiveFrom, LocalDate effectiveTo, RecordStatus status) {}
    public record AccountingRulePage(List<AccountingRuleResponse> content, int page, int size,
                                     long totalElements, int totalPages) {}

    public record SubledgerMappingRequest(
            @NotBlank @Size(max = 60) String mappingCode,
            @Size(max = 40) String productCode,
            @NotBlank @Size(max = 40) String glCode,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record SubledgerMappingResponse(String mappingCode, String productCode, String glCode,
                                           String currencyCode, LocalDate effectiveFrom, LocalDate effectiveTo,
                                           RecordStatus status) {}
    public record SubledgerMappingPage(List<SubledgerMappingResponse> content, int page, int size,
                                       long totalElements, int totalPages) {}

    public record TrialBalanceRequest(
            @NotNull LocalDate businessDate,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotBlank @Size(max = 100) String generatedBy,
            @Positive Integer executionEpoch) {
        public TrialBalanceRequest {
            executionEpoch = executionEpoch == null ? 1 : executionEpoch;
        }

        public TrialBalanceRequest(LocalDate businessDate, String currencyCode, String generatedBy) {
            this(businessDate, currencyCode, generatedBy, 1);
        }
    }
    public record TrialBalanceLineResponse(String glCode, BigDecimal debitTotal, BigDecimal creditTotal,
                                           BigDecimal closingBalance) {}
    public record TrialBalanceResponse(String runId, LocalDate businessDate, String currencyCode,
                                       BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced,
                                       String generatedBy, OffsetDateTime generatedAt,
                                       List<TrialBalanceLineResponse> lines) {}
    public record TrialBalancePage(List<TrialBalanceResponse> content, int page, int size,
                                   long totalElements, int totalPages) {}

    public record FinancialReconciliationRequest(
            @NotBlank @Size(max = 80) String eodRunId,
            @Size(max = 40) String stepCode,
            @Size(max = 100) String commandReference,
            @NotNull LocalDate businessDate,
            @Size(max = 80) String reconciledService,
            @Size(max = 64) String journalCorrelationId,
            @JsonAlias("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @Min(0) long expectedJournalCount,
            @NotNull @DecimalMin(value = "0.0000", inclusive = true) BigDecimal expectedTotalDebit,
            @Positive Integer executionEpoch) {
        public FinancialReconciliationRequest {
            executionEpoch = executionEpoch == null ? 1 : executionEpoch;
        }

        public FinancialReconciliationRequest(String eodRunId, String stepCode, String commandReference,
                                              LocalDate businessDate, String reconciledService,
                                              String journalCorrelationId, String currencyCode,
                                              long expectedJournalCount, BigDecimal expectedTotalDebit) {
            this(eodRunId, stepCode, commandReference, businessDate, reconciledService, journalCorrelationId,
                    currencyCode, expectedJournalCount, expectedTotalDebit, 1);
        }

        public FinancialReconciliationRequest(String eodRunId, String stepCode, String commandReference,
                                              LocalDate businessDate, String reconciledService,
                                              String currencyCode, long expectedJournalCount,
                                              BigDecimal expectedTotalDebit) {
            this(eodRunId, stepCode, commandReference, businessDate, reconciledService, null, currencyCode,
                    expectedJournalCount, expectedTotalDebit, 1);
        }
    }

    public record ReconciliationItemResponse(String itemId, String reference, BigDecimal expectedAmount,
                                             BigDecimal actualAmount, BigDecimal difference, boolean blocking,
                                             ReconciliationItemStatus status, String resolution,
                                             String resolvedBy, OffsetDateTime resolvedAt) {}
    public record FinancialReconciliationResponse(String runId, String eodRunId, LocalDate businessDate,
                                                  String currencyCode, long expectedJournalCount,
                                                  long actualJournalCount, BigDecimal expectedTotalDebit,
                                                  BigDecimal actualTotalDebit, ReconciliationStatus status,
                                                  List<ReconciliationItemResponse> items) {}
    public record FinancialReconciliationPage(List<FinancialReconciliationResponse> content, int page, int size,
                                              long totalElements, int totalPages) {}
    public record ReconciliationResolutionRequest(
            @NotNull ReconciliationItemStatus status,
            @NotBlank @Size(max = 1000) String resolution,
            @NotBlank @Size(max = 100) String actorId) {}
    public record ReconciliationRunResolutionRequest(
            @NotBlank String itemId,
            @NotNull ReconciliationItemStatus status,
            @NotBlank @Size(max = 1000) String resolution,
            @NotBlank @Size(max = 100) String actorId) {}

    public record AccountingPeriodCommand(
            @NotBlank @Size(max = 80) String eodRunId,
            @Size(max = 40) String stepCode,
            @Size(max = 100) String commandReference,
            @NotBlank @Size(max = 100) String actorId) {}
    public record AccountingPeriodResponse(LocalDate businessDate, PeriodStatus status, OffsetDateTime openedAt,
                                           OffsetDateTime closedAt, String openedBy, String closedBy,
                                           long version) {}

    public record AccountingDashboardResponse(
            LocalDate businessDate,
            long journalCount,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            long unbalancedJournalCount,
            long failedJournalCount,
            PeriodStatus periodStatus,
            long reconciliationAlertCount,
            List<JournalResponse> recentJournals) {}

    public record AccountingEodRunResponse(
            String eodRunId,
            LocalDate businessDate,
            String currencyCode,
            String status,
            int trialBalanceRuns,
            ReconciliationStatus reconciliationStatus,
            PeriodStatus periodStatus,
            List<String> blockers) {}
    public record AccountingEodRunPage(List<AccountingEodRunResponse> content, int page, int size,
                                       long totalElements, int totalPages) {}
}
