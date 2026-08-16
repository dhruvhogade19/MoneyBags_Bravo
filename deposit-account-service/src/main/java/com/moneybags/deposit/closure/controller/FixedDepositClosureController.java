package com.moneybags.deposit.closure.controller;

import com.moneybags.deposit.closure.dto.AccountClosureRequests.PrematureClosureQuoteRequest;
import com.moneybags.deposit.closure.dto.AccountClosureRequests.PrematureClosureRequest;
import com.moneybags.deposit.closure.dto.AccountClosureResponses.ClosureRequestView;
import com.moneybags.deposit.closure.dto.AccountClosureResponses.PrematureClosureQuoteResponse;
import com.moneybags.deposit.closure.service.FixedDepositClosureService;
import com.moneybags.deposit.service.Hashing;
import com.moneybags.deposit.service.IdempotentMutationExecutor;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/deposit-accounts/fixed-deposits/{fdId}")
public class FixedDepositClosureController {
    private final FixedDepositClosureService service;
    private final IdempotentMutationExecutor idempotency;

    public FixedDepositClosureController(FixedDepositClosureService service,
                                         IdempotentMutationExecutor idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping("/premature-closure-quotes")
    @PreAuthorize("@depositAuthorization.canAccessFixedDeposit(authentication, #fdId)")
    public PrematureClosureQuoteResponse quote(@PathVariable String fdId,
            @Valid @RequestBody PrematureClosureQuoteRequest request) {
        return service.quote(fdId, request);
    }

    @PostMapping("/premature-closure-requests")
    @PreAuthorize("@depositAuthorization.canAccessFixedDeposit(authentication, #fdId)")
    public ResponseEntity<ClosureRequestView> close(@PathVariable String fdId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PrematureClosureRequest request, Authentication authentication) {
        ClosureRequestView result = idempotency.execute("FIXED_DEPOSIT_PREMATURE_CLOSURE", key,
                List.of(fdId, request), ClosureRequestView.class,
                () -> service.close(fdId, request, actor(authentication), correlationId(), reference(key)));
        return ResponseEntity.created(URI.create("/api/deposit-accounts/fixed-deposits/" + fdId
                + "/premature-closure-requests/" + result.closureRequestId())).body(result);
    }

    @GetMapping("/premature-closure-requests/{requestId}")
    @PreAuthorize("@depositAuthorization.canAccessFixedDeposit(authentication, #fdId)")
    public ClosureRequestView get(@PathVariable String fdId, @PathVariable String requestId) {
        return service.get(fdId, requestId);
    }

    private String actor(Authentication authentication) {
        return Optional.ofNullable(authentication).map(Authentication::getName).orElse("local-user");
    }

    private String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");
    }

    private String reference(String key) {
        return "FD-PC-" + Hashing.sha256(key).substring(0, 40);
    }
}
