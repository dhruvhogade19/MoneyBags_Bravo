package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.domain.DomainTypes.*;
import com.moneybags.accounting.entity.*;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LifecycleService {
    private final SubledgerAccountRepository accounts;
    private final AccountLifecycleEventRepository events;
    private final LedgerBalanceService balances;
    private final Hashing hashing;
    private final AuditService audit;
    private final boolean enforceRegistration;

    public LifecycleService(SubledgerAccountRepository accounts, AccountLifecycleEventRepository events,
                            LedgerBalanceService balances, Hashing hashing, AuditService audit,
                            @Value("${moneybags.accounting.enforce-account-registration:false}")
                            boolean enforceRegistration) {
        this.accounts = accounts; this.events = events; this.balances = balances; this.hashing = hashing;
        this.audit = audit; this.enforceRegistration = enforceRegistration;
    }

    @Transactional
    public synchronized AccountLifecycleResponse register(AccountLifecycleEventRequest request, String key,
                                                          String correlationId) {
        validateEventType(request);
        String requestHash = hashing.requestHash(request);
        String keyHash = hashing.sha256(key);
        Optional<AccountLifecycleEvent> byReference = events.findByEventReference(request.eventReference());
        if (byReference.isPresent()) {
            AccountLifecycleEvent value = byReference.get();
            if (value.getRequestHash().equals(requestHash) || sameClosureTransition(value, request)) {
                return response(value, true);
            }
            throw idempotencyConflict();
        }
        Optional<AccountLifecycleEvent> byKey = events.findByIdempotencyKeyHash(keyHash);
        if (byKey.isPresent()) {
            AccountLifecycleEvent value = byKey.get();
            if (!value.getRequestHash().equals(requestHash)) throw idempotencyConflict();
            return response(value, true);
        }

        String source = request.accountType() == AccountType.DEPOSIT_ACCOUNT
                ? "DEPOSIT-ACCOUNT-SERVICE" : "CREDIT-CARD-SERVICE";
        if (isOpening(request.eventType())) {
            if (accounts.findByAccountTypeAndAccountReference(request.accountType(), request.accountReference()).isPresent())
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_LIFECYCLE_TRANSITION",
                        "Only an unregistered account can transition to OPEN");
            if (request.productCode() == null || request.productCode().isBlank())
                throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_CODE_REQUIRED",
                        "productCode is required for an opening event");
            accounts.save(new SubledgerAccount(UUID.randomUUID().toString(), request.accountType(),
                    request.accountReference(), request.productCode(), request.currencyCode(), source,
                    request.occurredAt()));
        } else {
            SubledgerAccount account = accounts.findForUpdate(request.accountType(), request.accountReference())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_REGISTERED",
                            "The account is not registered in Accounting"));
            if (account.getLifecycleState() != LifecycleState.OPEN) throw new ApiException(HttpStatus.CONFLICT,
                    "INVALID_LIFECYCLE_TRANSITION", "Only an OPEN account can transition to CLOSED");
            AccountClearanceResponse clearance = balances.clearance(request.accountType(), request.accountReference(),
                    request.currencyCode());
            if (!clearance.accountingCleared()) throw new ApiException(HttpStatus.CONFLICT,
                    "ACCOUNT_NOT_CLEARED", "Accounting balances are not clear for account closure");
            account.close(request.occurredAt());
        }

        AccountLifecycleEvent saved = events.save(new AccountLifecycleEvent(UUID.randomUUID().toString(),
                request.eventReference(), keyHash, requestHash, request.eventType(), request.accountType(),
                request.accountReference(), request.businessDate(), request.occurredAt(), request.reasonCode(),
                source, correlationId));
        audit.record(request.accountReference(), request.eventType().name(), "SUCCESS", source, "SERVICE", correlationId);
        return response(saved, false);
    }

    @Transactional(readOnly = true)
    public AccountClearanceResponse clearance(AccountType type, String reference, String currency) {
        return balances.clearance(type, reference, currency);
    }

    @Transactional
    public void lockAndValidateForPosting(Map<AccountType, Set<String>> references) {
        references.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                entry.getValue().stream().filter(Objects::nonNull).sorted().forEach(reference -> {
                    Optional<SubledgerAccount> account = accounts.findForUpdate(entry.getKey(), reference);
                    if (account.isEmpty()) {
                        if (enforceRegistration) throw new ApiException(HttpStatus.CONFLICT,
                                "ACCOUNT_NOT_REGISTERED", "Account must be registered before posting: " + reference);
                        return;
                    }
                    if (account.get().getLifecycleState() == LifecycleState.CLOSED)
                        throw new ApiException(HttpStatus.CONFLICT, "POSTING_TO_CLOSED_ACCOUNT",
                                "Ordinary postings are not allowed for a closed account: " + reference);
                }));
    }

    private void validateEventType(AccountLifecycleEventRequest request) {
        boolean deposit = request.eventType().name().startsWith("DEPOSIT_");
        if (deposit != (request.accountType() == AccountType.DEPOSIT_ACCOUNT))
            throw new ApiException(HttpStatus.BAD_REQUEST, "EVENT_ACCOUNT_TYPE_MISMATCH",
                    "eventType and accountType do not describe the same account family");
    }
    private boolean sameClosureTransition(AccountLifecycleEvent value, AccountLifecycleEventRequest request) {
        // The event reference identifies the one terminal closure transition. A caller can commit here and
        // subsequently roll back locally, then regenerate temporal metadata while retrying that same transition.
        return !isOpening(request.eventType())
                && value.getEventType() == request.eventType()
                && value.getAccountType() == request.accountType()
                && value.getAccountReference().equals(request.accountReference())
                && Objects.equals(value.getReasonCode(), request.reasonCode())
                && (request.productCode() == null || request.productCode().isBlank())
                && accounts.findByAccountTypeAndAccountReference(request.accountType(), request.accountReference())
                        .map(account -> account.getCurrencyCode().trim().equals(request.currencyCode()))
                        .orElse(false);
    }
    private ApiException idempotencyConflict() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "Lifecycle reference or idempotency key was reused with different content");
    }
    private boolean isOpening(LifecycleEventType type) { return type.name().endsWith("_OPENED"); }
    private AccountLifecycleResponse response(AccountLifecycleEvent value, boolean replay) {
        LifecycleState state = isOpening(value.getEventType()) ? LifecycleState.OPEN : LifecycleState.CLOSED;
        return new AccountLifecycleResponse(value.getEventReference(), value.getAccountType(),
                value.getAccountReference(), state, value.getProcessedAt(), value.getCorrelationId(), replay);
    }
}
