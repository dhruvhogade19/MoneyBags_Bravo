package com.moneybags.deposit.fixeddeposit.controller;

import com.moneybags.deposit.dto.PaymentOperationRequests.FixedDepositPayoutConfirmationRequest;
import com.moneybags.deposit.dto.PaymentOperationResponses.FixedDepositPayoutConfirmationView;
import com.moneybags.deposit.fixeddeposit.service.FixedDepositPayoutConfirmationService;
import jakarta.validation.Valid;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/deposit-accounts/fixed-deposits")
public class FixedDepositPayoutController {
    private final FixedDepositPayoutConfirmationService service;
    public FixedDepositPayoutController(FixedDepositPayoutConfirmationService service){this.service=service;}

    @PostMapping("/{fixedDepositId}/payout-confirmations")
    public FixedDepositPayoutConfirmationView confirm(@PathVariable String fixedDepositId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FixedDepositPayoutConfirmationRequest request,Authentication authentication) {
        return service.confirm(fixedDepositId,request,actor(authentication),correlationId());
    }
    private String actor(Authentication authentication){return Optional.ofNullable(authentication).map(Authentication::getName).orElse("payments-service");}
    private String correlationId(){return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");}
}
