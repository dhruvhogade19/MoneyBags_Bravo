package com.moneybags.deposit.fixeddeposit.controller;

import com.moneybags.deposit.dto.PaymentOperationRequests.FixedDepositFundingReservationRequest;
import com.moneybags.deposit.dto.PaymentOperationRequests.FixedDepositFundingSettlementRequest;
import com.moneybags.deposit.dto.PaymentOperationRequests.ReleaseReservationRequest;
import com.moneybags.deposit.dto.PaymentOperationResponses.FixedDepositFundingReservationView;
import com.moneybags.deposit.dto.PaymentOperationResponses.FixedDepositFundingSettlementView;
import com.moneybags.deposit.fixeddeposit.service.FixedDepositApplicationService;
import jakarta.validation.Valid;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/deposit-payment-operations")
public class FixedDepositPaymentController {
    private final FixedDepositApplicationService service;
    public FixedDepositPaymentController(FixedDepositApplicationService service){this.service=service;}

    @PostMapping("/fixed-deposit-funding/reservations")
    public ResponseEntity<FixedDepositFundingReservationView> reserve(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FixedDepositFundingReservationRequest request,Authentication authentication){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserveFunding(request,actor(authentication),correlationId()));
    }

    @PostMapping("/fixed-deposit-funding/{paymentId}/settle")
    public FixedDepositFundingSettlementView settle(@PathVariable String paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FixedDepositFundingSettlementRequest request,Authentication authentication){
        return service.settleFunding(paymentId,request,actor(authentication),correlationId());
    }

    @PostMapping("/reservations/{reservationId}/release")
    public FixedDepositFundingReservationView release(@PathVariable String reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReleaseReservationRequest request,Authentication authentication){
        return service.releaseFunding(reservationId,request,actor(authentication),correlationId());
    }

    private String actor(Authentication authentication){return Optional.ofNullable(authentication).map(Authentication::getName).orElse("payments-service");}
    private String correlationId(){return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");}
}
