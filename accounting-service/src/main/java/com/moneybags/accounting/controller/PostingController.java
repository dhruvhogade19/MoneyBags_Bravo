package com.moneybags.accounting.controller;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.service.PostingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class PostingController {
    private final PostingService postings;
    public PostingController(PostingService postings) { this.postings = postings; }

    @PostMapping("/internal/v1/payment-postings/settlements")
    ResponseEntity<JournalResponse> payment(@Valid @RequestBody PaymentSettlementPostingRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return createdOrReplay(postings.postPayment(request, key, correlation(), "PAYMENTS-SERVICE"));
    }

    @PostMapping("/internal/v1/payment-postings/refunds")
    ResponseEntity<JournalResponse> refund(@Valid @RequestBody PaymentRefundPostingRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return createdOrReplay(postings.postRefund(request, key, correlation(), "PAYMENTS-SERVICE"));
    }

    @GetMapping("/internal/v1/payment-postings/by-reference/{externalReference}")
    PostingOutcomeResponse paymentOutcome(@PathVariable String externalReference) {
        return postings.outcome(externalReference);
    }

    @PostMapping("/internal/v1/bill-postings")
    ResponseEntity<JournalResponse> bill(@Valid @RequestBody BillAccountingPostingRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return createdOrReplay(postings.postBill(request, key, correlation(), "BILL-GENERATION-SERVICE"));
    }

    @PostMapping("/internal/v1/fixed-deposit-postings")
    ResponseEntity<JournalResponse> fixedDeposit(@Valid @RequestBody FixedDepositPostingRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        return createdOrReplay(postings.postFixedDeposit(request, key, correlation(),
                "DEPOSIT-ACCOUNT-SERVICE", null));
    }

    @PostMapping("/internal/v1/fixed-deposit-postings/{operation:fundings|interest-accruals|interest-payouts|maturity-payouts|premature-closures}")
    ResponseEntity<JournalResponse> fixedDepositAlias(@PathVariable String operation,
            @Valid @RequestBody FixedDepositPostingRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        String type = switch (operation) {
            case "fundings" -> "FUNDING";
            case "interest-accruals" -> "INTEREST_ACCRUAL";
            case "interest-payouts" -> "INTEREST_PAYOUT";
            case "maturity-payouts" -> "MATURITY_PAYOUT";
            case "premature-closures" -> "PREMATURE_CLOSURE";
            default -> throw new IllegalArgumentException("Unsupported operation");
        };
        return createdOrReplay(postings.postFixedDeposit(request, key, correlation(),
                "DEPOSIT-ACCOUNT-SERVICE", type));
    }

    @GetMapping("/internal/v1/fixed-deposit-postings/by-reference/{postingReference}")
    PostingOutcomeResponse fixedDepositOutcome(@PathVariable String postingReference) {
        return postings.outcome(postingReference);
    }

    @PostMapping("/internal/v1/journals/{journalNumber}/reversals")
    ResponseEntity<JournalResponse> reverse(@PathVariable String journalNumber,
            @Valid @RequestBody JournalReversalRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 130) String key,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestHeader(value = "X-Source-Service", defaultValue = "PAYMENTS-SERVICE") String sourceService) {
        return createdOrReplay(postings.reverse(journalNumber, request, key, correlation(), sourceService));
    }

    private ResponseEntity<JournalResponse> createdOrReplay(JournalResponse response) {
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
    private String correlation() { return MDC.get("correlationId"); }
}
