package com.moneybags.deposit.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.moneybags.deposit.config.CorrelationIdFilter;
import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.domain.DomainTypes.LimitType;
import com.moneybags.deposit.dto.AccountRequests.*;
import com.moneybags.deposit.dto.AccountResponses.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.service.DepositAccountApplicationService;
import com.moneybags.deposit.service.IdempotentMutationExecutor;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/deposit-accounts")
public class DepositAccountController {
    private final DepositAccountApplicationService service;
    private final IdempotentMutationExecutor idempotency;

    public DepositAccountController(DepositAccountApplicationService service,
                                    IdempotentMutationExecutor idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<AccountDetailView> open(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody OpenDepositAccountRequest request,
                                                   Authentication authentication) {
        AccountDetailView result = service.open(request, idempotencyKey, actor(authentication), correlationId());
        return ResponseEntity.created(URI.create("/api/deposit-accounts/" + result.accountId()))
                .eTag(etag(result.version())).body(result);
    }

    @PostMapping("/eligibility-check")
    public EligibilityResult eligibility(@Valid @RequestBody EligibilityCheckRequest request) {
        return service.checkEligibility(request);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailView> get(@PathVariable String accountId) {
        AccountDetailView result = service.get(accountId);
        return ResponseEntity.ok().eTag(etag(result.version())).body(result);
    }

    @GetMapping
    public Page<AccountSummaryView> search(@RequestParam(required = false) String customerId,
                                           @RequestParam(required = false) AccountStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return service.search(customerId, status, PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{accountId}/balance")
    public BalanceView balance(@PathVariable String accountId) {
        return service.balance(accountId);
    }

    @GetMapping("/{accountId}/status-history")
    public List<StatusHistoryView> history(@PathVariable String accountId) {
        return service.history(accountId);
    }

    @PostMapping("/{accountId}/holders")
    public ResponseEntity<AccountDetailView> addHolder(@PathVariable String accountId,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        @Valid @RequestBody HolderRequest request,
                                                        Authentication authentication) {
        AccountDetailView result = idempotency.execute("ADD_ACCOUNT_HOLDER", idempotencyKey,
                List.of(accountId, request), AccountDetailView.class,
                () -> service.addHolder(accountId, request, actor(authentication), correlationId()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(result.version())).body(result);
    }

    @DeleteMapping("/{accountId}/holders/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHolder(@PathVariable String accountId, @PathVariable String customerId,
                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                             Authentication authentication) {
        idempotency.executeVoid("REMOVE_ACCOUNT_HOLDER", idempotencyKey, List.of(accountId, customerId),
                () -> service.removeHolder(accountId, customerId, actor(authentication), correlationId()));
    }

    @PutMapping("/{accountId}/nominees")
    public List<NomineeView> replaceNominees(@PathVariable String accountId,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody List<@Valid NomineeRequest> requests,
                                             Authentication authentication) {
        return idempotency.execute("REPLACE_ACCOUNT_NOMINEES", idempotencyKey, List.of(accountId, requests),
                new TypeReference<List<NomineeView>>() {},
                () -> service.replaceNominees(accountId, requests, actor(authentication), correlationId()));
    }

    @PutMapping("/{accountId}/limits/{limitType}")
    public LimitView upsertLimit(@PathVariable String accountId, @PathVariable LimitType limitType,
                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                 @Valid @RequestBody LimitRequest request, Authentication authentication) {
        return idempotency.execute("UPSERT_ACCOUNT_LIMIT", idempotencyKey,
                List.of(accountId, limitType, request), LimitView.class,
                () -> service.upsertLimit(accountId, limitType, request, actor(authentication), correlationId()));
    }

    @PostMapping("/{accountId}/mandates")
    public ResponseEntity<MandateView> addMandate(@PathVariable String accountId,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody MandateRequest request,
                                                  Authentication authentication) {
        MandateView result = idempotency.execute("ADD_ACCOUNT_MANDATE", idempotencyKey,
                List.of(accountId, request), MandateView.class,
                () -> service.addMandate(accountId, request, actor(authentication), correlationId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{accountId}/mandates/{mandateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeMandate(@PathVariable String accountId, @PathVariable String mandateId,
                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                              Authentication authentication) {
        idempotency.executeVoid("REVOKE_ACCOUNT_MANDATE", idempotencyKey, List.of(accountId, mandateId),
                () -> service.revokeMandate(accountId, mandateId, actor(authentication), correlationId()));
    }

    @PostMapping("/{accountId}/commands/{command}")
    public ResponseEntity<AccountDetailView> command(@PathVariable String accountId,
                                                      @PathVariable String command,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                      @Valid @RequestBody StatusCommand request,
                                                      Authentication authentication) {
        Long expectedVersion = parseEtag(ifMatch);
        AccountDetailView result = idempotency.execute("ACCOUNT_LIFECYCLE_COMMAND", idempotencyKey,
                List.of(accountId, command, request, expectedVersion == null ? "" : expectedVersion),
                AccountDetailView.class,
                () -> service.command(accountId, command, request, expectedVersion,
                        actor(authentication), correlationId()));
        return ResponseEntity.ok().eTag(etag(result.version())).body(result);
    }

    private Long parseEtag(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.replace("W/", "").replace("\"", "").trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ETAG", "If-Match must contain a numeric version");
        }
    }

    private String etag(long version) { return "\"" + version + "\""; }
    private String actor(Authentication authentication) {
        return Optional.ofNullable(authentication).map(Authentication::getName).orElse("local-user");
    }
    private String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");
    }
}
