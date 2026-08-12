package com.moneybags.deposit.controller;

import com.moneybags.deposit.dto.PaymentOperationRequests.*;
import com.moneybags.deposit.dto.PaymentOperationResponses.PaymentOperationView;
import com.moneybags.deposit.service.PaymentOperationService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/internal/deposit-payment-operations")
public class DepositPaymentOperationController {
    private final PaymentOperationService service;

    public DepositPaymentOperationController(PaymentOperationService service) { this.service = service; }

    @PostMapping("/book-transfers/reservations")
    public ResponseEntity<PaymentOperationView> reserveBookTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BookTransferReservationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserveBookTransfer(request, idempotencyKey,
                actor(authentication), correlationId()));
    }

    @PostMapping("/book-transfers/{paymentId}/settle")
    public PaymentOperationView settleBookTransfer(@PathVariable String paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SettlementRequest request, Authentication authentication) {
        return service.settleBookTransfer(paymentId, request, idempotencyKey, actor(authentication), correlationId());
    }

    @PostMapping("/credit-card-repayments/reservations")
    public ResponseEntity<PaymentOperationView> reserveCardRepayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CardRepaymentReservationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserveCardRepayment(request, idempotencyKey,
                actor(authentication), correlationId()));
    }

    @PostMapping("/credit-card-repayments/{paymentId}/capture")
    public PaymentOperationView captureCardRepayment(@PathVariable String paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SettlementRequest request, Authentication authentication) {
        return service.captureCardRepayment(paymentId, request, idempotencyKey,
                actor(authentication), correlationId());
    }

    @PostMapping("/reservations/{reservationId}/release")
    public PaymentOperationView release(@PathVariable String reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReleaseReservationRequest request, Authentication authentication) {
        return service.release(reservationId, request, idempotencyKey, actor(authentication), correlationId());
    }

    @GetMapping("/{paymentId}")
    public PaymentOperationView get(@PathVariable String paymentId) { return service.get(paymentId); }

    private String actor(Authentication authentication) {
        return Optional.ofNullable(authentication).map(Authentication::getName).orElse("payments-service");
    }
    private String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");
    }
}
