package com.moneybags.payments.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class IntegrationDtos {
  private IntegrationDtos() { }

  public record AccountEligibility(boolean debitAllowed, boolean creditAllowed,
                                   String status, String message) { }

  public record BookTransferReservationRequest(
      String paymentId, Long requestorCustomerId, String sourceAccountId,
      String targetAccountId, BigDecimal amount, String currencyCode, Instant expiresAt) { }

  public record CardRepaymentReservationRequest(
      String paymentId, Long requestorCustomerId, String sourceAccountId,
      String creditCardAccountId, BigDecimal amount, String currencyCode, Instant expiresAt) { }

  public record ReservationResponse(
      String reservationId, String paymentId, String status, Instant expiresAt) { }

  public record ReservationCommand(String reservationId) { }
  public record ReleaseReservationRequest(String paymentId, String reasonCode) { }
  public record DepositOperationResponse(
      String paymentId, String reservationId, String status,
      String sourceTransactionId, String targetTransactionId) { }

  public record FixedDepositFundingReservationRequest(
      String paymentId, Long requestorCustomerId, String sourceAccountId,
      String fixedDepositId, BigDecimal amount, String currencyCode, Instant expiresAt) { }

  public record FixedDepositFundingReservationResponse(
      String reservationId, String paymentId, String operationType, String status,
      String sourceAccountId, String targetAccountId, String fixedDepositId,
      BigDecimal amount, String currencyCode, Instant expiresAt) { }

  public record FixedDepositFundingSettlementRequest(
      String reservationId, String fixedDepositId, String journalNumber) { }

  public record FixedDepositFundingSettlementResponse(
      String reservationId, String paymentId, String operationType, String status,
      String fixedDepositId, String fixedDepositStatus, java.util.List<String> transactionIds) { }

  public record FixedDepositPayoutConfirmationRequest(
      String paymentId, String journalNumber, String payoutAccountId,
      BigDecimal principalAmount, BigDecimal interestAmount, BigDecimal netPayoutAmount,
      String currencyCode, String payoutType) { }

  public record FixedDepositPayoutConfirmationResponse(
      String fixedDepositId, String paymentId, String status, String payoutAccountId,
      BigDecimal netPayoutAmount, String currencyCode, Instant completedAt) { }

  public record CardHoldRequest(String referenceId, BigDecimal amount) { }
  public record CardHoldResponse(
      Long holdId, Long accountId, String referenceId, BigDecimal amount,
      String status, Instant createdAt) { }

  public record CardBillPaymentRequest(BigDecimal amount) { }
  public record CardAccountResponse(
      String accountId, String applicationId, Long cifId, String productCode,
      String cardNumber, BigDecimal sanctionedLimit,
      BigDecimal purchaseInterestRateSnapshot, BigDecimal availableLimit,
      BigDecimal outstandingAmount, String status, Instant openedAt) { }

  public record AccountingInstrument(String instrumentType, String accountId, String merchantId) {
    public AccountingInstrument(String instrumentType, String accountId) {
      this(instrumentType, accountId, null);
    }
  }
  public record AccountingSettlementRequest(
      String paymentId, String paymentType, AccountingInstrument source,
      AccountingInstrument destination, BigDecimal amount, String currencyCode,
      Instant occurredAt, LocalDate businessDate, String reference) { }

  public record AccountingResponse(
      String journalNumber, Long postingSequence, String externalReference,
      String sourceService, String eventType, Instant occurredAt, LocalDate businessDate,
      String currencyCode, String status, BigDecimal totalDebit, BigDecimal totalCredit,
      String correlationId, Instant postedAt, Boolean idempotentReplay,
      @JsonAlias("reversalOfJournalNumber") String reversesJournalNumber) { }

  public record AccountingLookupResponse(
      String externalReference, @JsonAlias("outcome") String status, String journalNumber,
      Instant receivedAt, Instant completedAt, String rejectionCode,
      String rejectionMessage, AccountingResponse journal) { }

  public record AccountingReversalRequest(
      String paymentId, LocalDate businessDate, Instant occurredAt, String reason) { }

  public record FixedDepositAccountingComponent(
      String componentType, BigDecimal amount) { }

  public record FixedDepositAccountingRequest(
      String postingReference, String postingType, String fixedDepositAccountId,
      String productCode, String currencyCode, LocalDate businessDate,
      OffsetDateTime occurredAt, List<FixedDepositAccountingComponent> components,
      String fundingAccountId, String payoutAccountId, String payoutMode,
      String reasonCode, String narration) { }

  public record BillSummary(
      String billId, String accountId, String billingPeriod, String status,
      BigDecimal previousBalance, BigDecimal totalAmountDue, BigDecimal minimumAmountDue,
      BigDecimal paidAmount, BigDecimal outstandingAmount, LocalDate paymentDueDate,
      String currency, List<Map<String, Object>> lines) { }

  public record BillPaymentSettlementRequest(
      String paymentId, String journalNumber, BigDecimal amount,
      String currency, Instant settledAt) { }

  public record NotificationRequest(
      Long cifId, String notificationType, String sourceReference,
      Map<String, String> templateVariables) { }

  public record NotificationResponse(
      String notificationId, Long cifId, String notificationType,
      String sourceReference, String status, Instant createdAt, Instant sentAt,
      Boolean idempotentReplay) { }
}
