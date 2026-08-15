package com.moneybags.payments.api;

import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.dto.PaymentDtos.*;
import com.moneybags.payments.service.EodControlService;
import com.moneybags.payments.service.PaymentOrchestrationService;
import com.moneybags.payments.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@Tag(name = "Internal Payments",
    description = "Fixed-deposit payouts, Statements, EOD and recovery operations")
public class InternalPaymentController {
  private final PaymentQueryService queries;
  private final PaymentOrchestrationService orchestration;
  private final EodControlService eod;

  public InternalPaymentController(PaymentQueryService queries,
                                   PaymentOrchestrationService orchestration,
                                   EodControlService eod) {
    this.queries = queries;
    this.orchestration = orchestration;
    this.eod = eod;
  }

  @GetMapping("/internal/payments")
  @Operation(summary = "Get settled and reversed account activity for Statements")
  public PageResponse<StatementActivity> statements(
      @RequestParam @NotBlank String accountId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    return queries.statements(accountId, from, to, page, size);
  }

  @GetMapping("/internal/v1/payments")
  @Operation(summary = "List payments for EOD and operations")
  public PageResponse<PaymentResponse> internal(
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate businessDate,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    return queries.internal(status, businessDate, page, size);
  }

  @PostMapping("/internal/v1/payments")
  @Operation(summary = "Initiate a fixed-deposit maturity or premature-closure payout")
  public PaymentResponse fixedDepositPayout(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody FixedDepositPayoutRequest request) {
    return orchestration.fixedDepositPayout(request, idempotencyKey,
        org.slf4j.MDC.get("correlationId"));
  }

  @PostMapping("/internal/v1/payments/eod/cutoff")
  @Operation(summary = "Stop accepting new payments for a business date")
  public EodControlResponse cutoff(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody EodCutoffRequest request) {
    return eod.cutoff(request.businessDate());
  }

  @PostMapping("/internal/v1/payments/eod/drain")
  @Operation(summary = "Report whether all in-flight payments have drained")
  public EodControlResponse drain(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return eod.drain();
  }

  @PostMapping("/internal/v1/payments/eod/reopen")
  @Operation(summary = "Reopen payment intake after EOD (also useful for local Swagger testing)")
  public EodControlResponse reopen(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return eod.reopen();
  }

  @PostMapping("/internal/v1/payments/{paymentId}/reversal")
  @Operation(summary = "Retry the Accounting part of a pending reversal")
  public PaymentResponse reverse(@PathVariable String paymentId,
                                 @Valid @RequestBody ReversalRequest request) {
    return queries.completePendingReversal(paymentId, request.reason());
  }

  @PostMapping("/internal/v1/payments/{paymentId}/billing-settlement")
  @Operation(summary = "Retry Bill Generation callback for a financially completed repayment")
  public PaymentResponse retryBillingSettlement(
      @PathVariable String paymentId,
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return orchestration.retryBillSettlement(paymentId);
  }
}
