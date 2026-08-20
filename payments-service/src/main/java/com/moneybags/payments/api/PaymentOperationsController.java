package com.moneybags.payments.api;

import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.dto.PaymentDtos.EodControlResponse;
import com.moneybags.payments.dto.PaymentDtos.EodCutoffRequest;
import com.moneybags.payments.dto.PaymentDtos.PageResponse;
import com.moneybags.payments.dto.PaymentDtos.PaymentResponse;
import com.moneybags.payments.dto.PaymentDtos.ReversalRequest;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryCandidate;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryPreview;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryRequest;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryResponse;
import com.moneybags.payments.service.EodControlService;
import com.moneybags.payments.service.FixedDepositAccountingRecoveryService;
import com.moneybags.payments.service.PaymentOrchestrationService;
import com.moneybags.payments.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/operations")
@Validated
@PreAuthorize("hasAuthority('SCOPE_payment:admin') or hasRole('BANK_ADMIN')")
@Tag(name = "Payment Operations",
    description = "Admin-scoped payment search, EOD intake control and recovery actions")
public class PaymentOperationsController {
  private final PaymentQueryService queries;
  private final PaymentOrchestrationService orchestration;
  private final EodControlService eod;
  private final FixedDepositAccountingRecoveryService fixedDepositRecovery;

  public PaymentOperationsController(PaymentQueryService queries,
                                     PaymentOrchestrationService orchestration,
                                     EodControlService eod,
                                     FixedDepositAccountingRecoveryService fixedDepositRecovery) {
    this.queries = queries;
    this.orchestration = orchestration;
    this.eod = eod;
    this.fixedDepositRecovery = fixedDepositRecovery;
  }

  @GetMapping
  @Operation(summary = "Search payments for a business date and optional status")
  public PageResponse<PaymentResponse> list(
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate businessDate,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    return queries.internal(status, businessDate, page, size);
  }

  @PostMapping("/eod/cutoff")
  @Operation(summary = "Stop accepting new payments for a business date")
  public EodControlResponse cutoff(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody EodCutoffRequest request) {
    return eod.cutoff(request.businessDate(), request.currencyCode(), request.commandReference());
  }

  @PostMapping("/eod/drain")
  @Operation(summary = "Check whether all in-flight payments have drained")
  public EodControlResponse drain(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return eod.drain();
  }

  @PostMapping("/eod/reopen")
  @Operation(summary = "Reopen payment intake after EOD")
  public EodControlResponse reopen(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return eod.reopen();
  }

  @PostMapping("/{paymentId}/reversal")
  @Operation(summary = "Retry an Accounting reversal")
  public PaymentResponse reverse(
      @PathVariable String paymentId,
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody ReversalRequest request) {
    return queries.completePendingReversal(paymentId, request.reason());
  }

  @PostMapping("/{paymentId}/billing-settlement")
  @Operation(summary = "Retry the Billing callback for a completed repayment")
  public PaymentResponse retryBillingSettlement(
      @PathVariable String paymentId,
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    return orchestration.retryBillSettlement(paymentId);
  }

  @GetMapping("/fixed-deposit-funding-accounting-recovery/candidates")
  @Operation(summary = "List locally evidenced legacy FD funding recovery candidates")
  public PageResponse<FixedDepositAccountingRecoveryCandidate> recoveryCandidates(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    return fixedDepositRecovery.candidates(page, size);
  }

  @GetMapping("/fixed-deposit-funding-accounting-recovery/{paymentId}/preview")
  @Operation(summary = "Dry-run an FD funding recovery without creating a journal")
  public FixedDepositAccountingRecoveryPreview previewRecovery(
      @PathVariable String paymentId) {
    return fixedDepositRecovery.preview(paymentId, MDC.get("correlationId"));
  }

  @PostMapping("/fixed-deposit-funding-accounting-recovery/{paymentId}")
  @Operation(summary = "Apply an explicitly confirmed and audited FD funding recovery")
  public FixedDepositAccountingRecoveryResponse recoverFixedDepositFunding(
      @PathVariable String paymentId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey,
      @Valid @RequestBody FixedDepositAccountingRecoveryRequest request,
      Authentication authentication) {
    String actor = authentication == null || authentication.getName() == null
        || authentication.getName().isBlank() ? "local-bank-admin" : authentication.getName();
    return fixedDepositRecovery.execute(paymentId, idempotencyKey, request, actor,
        MDC.get("correlationId"));
  }
}
