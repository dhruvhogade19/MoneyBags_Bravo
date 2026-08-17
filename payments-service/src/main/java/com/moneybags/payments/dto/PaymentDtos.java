package com.moneybags.payments.dto;

import com.moneybags.payments.domain.InstrumentType;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PaymentDtos {
  private PaymentDtos() { }

  public record BookTransferRequest(
      @Schema(example = "101") @NotNull @Positive Long requestorCustomerId,
      @Schema(example = "dep-acc-001") @NotBlank @Size(max = 150) String sourceAccountId,
      @Schema(example = "dep-acc-002") @NotBlank @Size(max = 150) String targetAccountId,
      @Schema(example = "500.00") @NotNull @DecimalMin("0.01")
      @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @Schema(example = "INR") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
      @Schema(example = "Rent payment") @Size(max = 255) String reference) { }

  public record MerchantPaymentRequest(
      @Schema(example = "101") @NotNull @Positive Long requestorCustomerId,
      @Schema(example = "101") @NotBlank @Size(max = 150) String creditCardAccountId,
      @Schema(example = "MERCHANT-001") @NotBlank @Size(max = 150) String merchantId,
      @Schema(example = "50000.00") @NotNull @DecimalMin("0.01")
      @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @Schema(example = "INR") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
      @Schema(example = "Merchant purchase") @Size(max = 255) String reference) { }

  public record CardRepaymentRequest(
      @Schema(example = "101") @NotNull @Positive Long requestorCustomerId,
      @Schema(example = "BILL-202608-001") @NotBlank @Size(max = 100) String billId,
      @Schema(example = "dep-acc-001") @NotBlank @Size(max = 150) String sourceDepositAccountId,
      @Schema(example = "101") @NotBlank @Size(max = 150) String creditCardAccountId,
      @Schema(example = "25000.00") @NotNull @DecimalMin("0.01")
      @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @Schema(example = "INR") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
      @Schema(example = "Credit-card bill repayment") @Size(max = 255) String reference) { }

  public record FixedDepositFundingRequest(
      @Schema(example = "101") @NotNull @Positive Long requestorCustomerId,
      @Schema(example = "dep-acc-001") @NotBlank @Size(max = 150) String sourceAccountId,
      @Schema(example = "fd-001") @NotBlank @Size(max = 100) String fixedDepositId,
      @Schema(example = "100000.00") @NotNull @DecimalMin("0.01")
      @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @Schema(example = "INR") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
      @Schema(example = "Initial funding for fd-001") @Size(max = 255) String reference) { }

  public record FixedDepositPayoutRequest(
      @Schema(example = "FIXED_DEPOSIT_MATURITY_PAYOUT") @NotNull PaymentType paymentType,
      @Schema(example = "101") @NotNull @Positive Long requestorCustomerId,
      @Schema(example = "fd-account-001") @NotBlank @Size(max = 150) String sourceAccountId,
      @Schema(example = "DEPOSIT_ACCOUNT") @NotNull InstrumentType destinationType,
      @Schema(example = "dep-acc-001") @NotBlank @Size(max = 150)
      String destinationAccountId,
      @Schema(example = "106968.00") @NotNull @DecimalMin("0.01")
      @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @Schema(example = "100000.00") @NotNull @DecimalMin("0.00")
      @Digits(integer = 15, fraction = 4) BigDecimal principalAmount,
      @Schema(example = "6968.00") @NotNull @DecimalMin("0.00")
      @Digits(integer = 15, fraction = 4) BigDecimal interestAmount,
      @Schema(example = "INR") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
      @Schema(example = "FD maturity payout for fd-001") @Size(max = 255) String reference,
      @Schema(example = "fd-001") @NotBlank @Size(max = 100) String fixedDepositId) { }

  public record PaymentResponse(
      String paymentId,
      Long requestorCustomerId,
      PaymentType paymentType,
      InstrumentType sourceInstrumentType,
      String sourceAccountId,
      InstrumentType destinationInstrumentType,
      String destinationAccountId,
      String merchantId,
      String billId,
      String fixedDepositId,
      BigDecimal principalAmount,
      BigDecimal interestAmount,
      BigDecimal amount,
      String currencyCode,
      PaymentStatus status,
      String reference,
      String depositReservationId,
      String cardHoldId,
      String accountingJournalNumber,
      String reversalJournalNumber,
      String failureCode,
      String failureMessage,
      String correlationId,
      LocalDate businessDate,
      Instant createdAt,
      Instant updatedAt,
      Instant settledAt,
      Instant reversedAt) { }

  public record PageResponse<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) { }

  public record StatementActivity(
      String paymentId,
      String activityType,
      PaymentType paymentType,
      String accountId,
      String direction,
      String counterpartyAccountId,
      BigDecimal amount,
      String currencyCode,
      String description,
      Instant occurredAt,
      String originalPaymentId) { }

  public record EodCutoffRequest(
      @NotNull LocalDate businessDate,
      @NotBlank @Size(max = 100) String commandReference) { }

  public record EodControlResponse(
      LocalDate businessDate, String status, boolean newPaymentIntake, long pendingPayments) { }

  public record ReversalRequest(
      @NotBlank @Size(max = 250) @Schema(example = "Retrying compensation after peer recovery")
      String reason) { }

  public record InternalPaymentFilter(
      PaymentStatus status, LocalDate businessDate,
      @Min(0) int page, @Min(1) @Max(500) int size) { }
}
