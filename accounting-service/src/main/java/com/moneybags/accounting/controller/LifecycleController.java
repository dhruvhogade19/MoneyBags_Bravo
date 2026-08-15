package com.moneybags.accounting.controller;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.domain.DomainTypes.AccountType;
import com.moneybags.accounting.service.LifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class LifecycleController {
    private final LifecycleService lifecycle;
    public LifecycleController(LifecycleService lifecycle) { this.lifecycle = lifecycle; }

    @PostMapping("/internal/v1/account-lifecycle-events")
    ResponseEntity<AccountLifecycleResponse> register(@Valid @RequestBody AccountLifecycleEventRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        AccountLifecycleResponse response = lifecycle.register(request, key, MDC.get("correlationId"));
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/internal/v1/account-clearances/{accountType}/{accountReference}")
    AccountClearanceResponse clearance(@PathVariable AccountType accountType, @PathVariable String accountReference,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z]{3}") String currencyCode) {
        return lifecycle.clearance(accountType, accountReference, currencyCode);
    }
}
