package com.moneybags.accounting.controller;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.service.EodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@Validated
public class EodController {
    private final EodService eod;
    public EodController(EodService eod) { this.eod = eod; }

    @PostMapping("/internal/v1/trial-balances")
    ResponseEntity<TrialBalanceResponse> trialBalance(@Valid @RequestBody TrialBalanceRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eod.generateTrialBalance(request, key));
    }

    @GetMapping("/api/v1/trial-balances/{runId}")
    TrialBalanceResponse trialBalance(@PathVariable String runId) { return eod.getTrialBalance(runId); }

    @PostMapping("/internal/v1/eod/reconciliation/runs")
    ResponseEntity<FinancialReconciliationResponse> reconcile(
            @Valid @RequestBody FinancialReconciliationRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eod.reconcile(request, key));
    }

    @GetMapping("/api/v1/reconciliation/runs/{runId}")
    FinancialReconciliationResponse reconciliation(@PathVariable String runId) {
        return eod.getReconciliation(runId);
    }

    @PatchMapping("/api/v1/reconciliation/runs/{runId}/items/{itemId}/resolution")
    FinancialReconciliationResponse resolve(@PathVariable String runId, @PathVariable String itemId,
            @Valid @RequestBody ReconciliationResolutionRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key) {
        return eod.resolve(runId, itemId, request, key);
    }

    @PostMapping("/internal/v1/accounting-periods/{businessDate}/open")
    AccountingPeriodResponse open(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @Valid @RequestBody AccountingPeriodCommand request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return eod.openPeriod(businessDate, request, key);
    }

    @PostMapping("/internal/v1/accounting-periods/{businessDate}/close")
    AccountingPeriodResponse close(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @Valid @RequestBody AccountingPeriodCommand request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return eod.closePeriod(businessDate, request, key);
    }

    @GetMapping("/api/v1/accounting-periods/{businessDate}")
    AccountingPeriodResponse period(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                    LocalDate businessDate) {
        return eod.getPeriod(businessDate);
    }
}
