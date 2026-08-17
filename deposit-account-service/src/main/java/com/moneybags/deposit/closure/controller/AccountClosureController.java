package com.moneybags.deposit.closure.controller;

import com.moneybags.deposit.closure.dto.AccountClosureRequests.CancelClosureRequest;
import com.moneybags.deposit.closure.dto.AccountClosureRequests.CasaClosureRequest;
import com.moneybags.deposit.closure.dto.AccountClosureRequests.ClosureQuoteRequest;
import com.moneybags.deposit.closure.dto.AccountClosureResponses.ClosureQuoteResponse;
import com.moneybags.deposit.closure.dto.AccountClosureResponses.ClosureRequestView;
import com.moneybags.deposit.closure.service.CasaClosureService;
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
@RequestMapping("/api/deposit-accounts/{accountId}")
public class AccountClosureController {
    private final CasaClosureService service;
    private final IdempotentMutationExecutor idempotency;

    public AccountClosureController(CasaClosureService service, IdempotentMutationExecutor idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping("/closure-quotes")
    @PreAuthorize("@depositAuthorization.canManageAccount(authentication, #accountId)")
    public ClosureQuoteResponse quote(@PathVariable String accountId,
                                      @Valid @RequestBody ClosureQuoteRequest request) {
        return service.quote(accountId, request);
    }

    @PostMapping("/closure-requests")
    @PreAuthorize("@depositAuthorization.canManageAccount(authentication, #accountId)")
    public ResponseEntity<ClosureRequestView> close(@PathVariable String accountId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CasaClosureRequest request, Authentication authentication) {
        ClosureRequestView result = idempotency.execute("CASA_ACCOUNT_CLOSURE", key,
                List.of(accountId, request), ClosureRequestView.class,
                () -> service.close(accountId, request, actor(authentication), correlationId(), reference("CASA-CLOSE", key)));
        return ResponseEntity.created(URI.create("/api/deposit-accounts/" + accountId
                + "/closure-requests/" + result.closureRequestId())).body(result);
    }

    @GetMapping("/closure-requests/{requestId}")
    @PreAuthorize("@depositAuthorization.canAccessAccount(authentication, #accountId)")
    public ClosureRequestView get(@PathVariable String accountId, @PathVariable String requestId) {
        return service.get(accountId, requestId);
    }

    @GetMapping("/closure-requests")
    @PreAuthorize("@depositAuthorization.canAccessAccount(authentication, #accountId)")
    public List<ClosureRequestView> history(@PathVariable String accountId) {
        return service.history(accountId);
    }

    @PostMapping("/closure-requests/{requestId}/cancel")
    @PreAuthorize("@depositAuthorization.canManageAccount(authentication, #accountId)")
    public ClosureRequestView cancel(@PathVariable String accountId, @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CancelClosureRequest request,
            Authentication authentication) {
        return idempotency.execute("CANCEL_ACCOUNT_CLOSURE", key, List.of(accountId, requestId, request),
                ClosureRequestView.class,
                () -> service.cancel(accountId, requestId, request, actor(authentication), correlationId()));
    }

    private String actor(Authentication authentication) {
        return Optional.ofNullable(authentication).map(Authentication::getName).orElse("local-user");
    }

    private String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");
    }

    private String reference(String prefix, String key) {
        return prefix + "-" + Hashing.sha256(key).substring(0, 40);
    }
}
