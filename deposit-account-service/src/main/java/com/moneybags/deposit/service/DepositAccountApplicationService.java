package com.moneybags.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.config.DepositAccountProperties;
import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.dto.AccountRequests.*;
import com.moneybags.deposit.dto.AccountResponses.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.integration.BankingReferenceGateway;
import com.moneybags.deposit.integration.AccountingBalanceGateway;
import com.moneybags.deposit.integration.AccountingLifecycleGateway;
import com.moneybags.deposit.repository.*;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DepositAccountApplicationService {
    private static final String OPEN_SCOPE = "OPEN_DEPOSIT_ACCOUNT";

    private final DepositAccountRepository accountRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountLimitRepository limitRepository;
    private final AccountNomineeRepository nomineeRepository;
    private final AccountMandateRepository mandateRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AuditLogRepository auditRepository;
    private final BankingReferenceGateway referenceGateway;
    private final AccountingBalanceGateway accountingBalanceGateway;
    private final AccountingLifecycleGateway accountingLifecycleGateway;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountViewMapper viewMapper;
    private final PiiProtector piiProtector;
    private final NotificationOutboxService notificationOutbox;
    private final DepositAccountProperties properties;
    private final ObjectMapper objectMapper;

    public DepositAccountApplicationService(DepositAccountRepository accountRepository,
                                            AccountStatusHistoryRepository historyRepository,
                                            AccountLimitRepository limitRepository,
                                            AccountNomineeRepository nomineeRepository,
                                            AccountMandateRepository mandateRepository,
                                            IdempotencyRecordRepository idempotencyRepository,
                                            AuditLogRepository auditRepository,
                                            BankingReferenceGateway referenceGateway,
                                            AccountingBalanceGateway accountingBalanceGateway,
                                            AccountingLifecycleGateway accountingLifecycleGateway,
                                            AccountNumberGenerator accountNumberGenerator,
                                            AccountViewMapper viewMapper,
                                            PiiProtector piiProtector,
                                            NotificationOutboxService notificationOutbox,
                                            DepositAccountProperties properties,
                                            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.historyRepository = historyRepository;
        this.limitRepository = limitRepository;
        this.nomineeRepository = nomineeRepository;
        this.mandateRepository = mandateRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditRepository = auditRepository;
        this.referenceGateway = referenceGateway;
        this.accountingBalanceGateway = accountingBalanceGateway;
        this.accountingLifecycleGateway = accountingLifecycleGateway;
        this.accountNumberGenerator = accountNumberGenerator;
        this.viewMapper = viewMapper;
        this.piiProtector = piiProtector;
        this.notificationOutbox = notificationOutbox;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountDetailView open(OpenDepositAccountRequest request, String idempotencyKey,
                                  String actor, String correlationId) {
        requireIdempotencyKey(idempotencyKey);
        validateOpeningRequest(request);
        String keyHash = Hashing.sha256(idempotencyKey);
        String requestHash = Hashing.sha256(toJson(request));
        Optional<IdempotencyRecord> prior = idempotencyRepository.findByScopeAndKeyHash(OPEN_SCOPE, keyHash);
        if (prior.isPresent()) {
            IdempotencyRecord record = prior.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key was already used with a different request");
            }
            if ("COMPLETED".equals(record.getProcessingStatus()) && record.getResourceId() != null) {
                return get(record.getResourceId());
            }
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_PROCESSING",
                    "A request with this idempotency key is already processing");
        }

        IdempotencyRecord idempotency = new IdempotencyRecord(UUID.randomUUID().toString(), OPEN_SCOPE,
                keyHash, requestHash, OffsetDateTime.now().plusHours(properties.idempotencyTtlHours()));
        idempotencyRepository.save(idempotency);

        BankingReferenceGateway.ValidationResult validation = referenceGateway.validateAccountOpening(
                request.primaryCustomerId(), request.productId(), request.productVersion(), request.currency(),
                request.openingAmount());
        if (!validation.eligible()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOMER_OR_PRODUCT_NOT_ELIGIBLE",
                    eligibilityMessage(validation.decisionCode()));
        }
        if (!"SAVINGS".equalsIgnoreCase(validation.accountType())
                && !"CURRENT".equalsIgnoreCase(validation.accountType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_ACCOUNT_TYPE",
                    "Only Savings and Current products are supported");
        }

        String accountId = UUID.randomUUID().toString();
        DepositAccount account = new DepositAccount(accountId, accountNumberGenerator.next(), request.productId(),
                request.productVersion(), validation.productName(), ProductSubtype.valueOf(validation.accountType().toUpperCase()),
                request.currency(), request.servicingBranchId(),
                request.operatingInstruction(), request.externalReference(), actor);
        for (String customerId : new LinkedHashSet<>(request.customerIds())) {
            if (!customerId.equals(request.primaryCustomerId())) {
                BankingReferenceGateway.ValidationResult jointValidation = referenceGateway
                        .validateCustomerEligibility(customerId);
                if (!jointValidation.eligible()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "JOINT_HOLDER_NOT_ELIGIBLE", "A joint holder failed CIF or KYC validation");
            }
            HolderRole role = customerId.equals(request.primaryCustomerId()) ? HolderRole.PRIMARY : HolderRole.JOINT;
            account.addHolder(new AccountHolder(UUID.randomUUID().toString(), customerId, role,
                    role == HolderRole.PRIMARY ? request.operatingInstruction().name() : "JOINT_HOLDER", null));
        }
        AccountBalance openingBalance = AccountBalance.initial(request.currency(), UUID.randomUUID().toString());
        if (request.openingAmount().signum() > 0) {
            openingBalance.credit(request.openingAmount(), "INITIAL_FUNDING-" + accountId);
        }
        account.setBalanceProjection(openingBalance);
        accountRepository.save(account);
        OffsetDateTime openedAt = OffsetDateTime.now();
        accountingLifecycleGateway.publishOpening(new AccountingLifecycleGateway.AccountOpenedEvent(
                "DEPOSIT-OPEN:" + accountId, "DEPOSIT_ACCOUNT_OPENED", "DEPOSIT_ACCOUNT", accountId,
                request.productId(), request.currency(), openedAt.toLocalDate(), openedAt),
                "DEPOSIT-OPEN:" + accountId, correlationId);

        if (request.nominees() != null) {
            request.nominees().forEach(n -> nomineeRepository.save(new AccountNominee(UUID.randomUUID().toString(),
                    accountId, n.customerReference(), piiProtector.encrypt(n.name()), n.relationshipCode(),
                    n.allocationPercentage())));
        }
        historyRepository.save(new AccountStatusHistory(UUID.randomUUID().toString(), accountId, null,
                AccountStatus.PENDING_ACTIVATION, "ACCOUNT_OPENED", null, actor, "USER", correlationId));
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "OPEN_ACCOUNT", "SUCCESS",
                actor, "USER", "ACCOUNT_OPENED", null, Hashing.sha256(accountId + account.getAccountNumber()),
                correlationId));
        idempotency.setProcessingStatus("COMPLETED");
        idempotency.setResourceId(accountId);
        idempotency.setHttpStatus(201);
        idempotencyRepository.save(idempotency);
        notificationOutbox.enqueue(request.primaryCustomerId(), "DEPOSIT_ACCOUNT_CREATED", accountId,
                "deposit-account-" + accountId + "-created", Map.of(
                        "accountType", titleCase(validation.accountType()), "accountId", accountId));
        return viewMapper.detail(account);
    }

    private String titleCase(String value) {
        String normalized = value == null ? "Deposit" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Arrays.stream(normalized.split(" ")).filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1)).collect(java.util.stream.Collectors.joining(" "));
    }

    private static String eligibilityMessage(String decisionCode) {
        if ("KYC_APPROVAL_REQUIRED".equals(decisionCode)) {
            return "KYC approval is required before opening a savings or current account";
        }
        if ("PRODUCT_ELIGIBILITY_FAILED".equals(decisionCode)) {
            return "The selected product's eligibility rules rejected this account opening";
        }
        return "CIF, KYC or product validation rejected the account opening";
    }

    @Transactional(readOnly = true)
    public EligibilityResult checkEligibility(EligibilityCheckRequest request) {
        BankingReferenceGateway.ValidationResult value = referenceGateway.validateAccountOpening(request.customerId(),
                request.productId(), request.productVersion(), request.currency(), request.openingAmount());
        return new EligibilityResult(value.eligible(), value.decisionCode(), value.productName(), value.evaluatedAt());
    }

    @Transactional(readOnly = true)
    public AccountDetailView get(String accountId) {
        return viewMapper.detail(loadDetailed(accountId));
    }

    @Transactional(readOnly = true)
    public Page<AccountSummaryView> search(String customerId, AccountStatus status, Pageable pageable) {
        return accountRepository.search(blankToNull(customerId), status, pageable).map(viewMapper::summary);
    }

    @Transactional(readOnly = true)
    public BalanceView balance(String accountId) {
        return viewMapper.balance(loadDetailed(accountId).getBalance());
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryView> history(String accountId) {
        ensureExists(accountId);
        return historyRepository.findByAccountIdOrderByChangedAtAsc(accountId).stream().map(h ->
                new StatusHistoryView(h.getFromStatus(), h.getToStatus(), h.getReasonCode(), h.getReasonText(),
                        h.getChangedBy(), h.getActorType(), h.getChangedAt(), h.getCorrelationId())).toList();
    }

    @Transactional
    public AccountDetailView addHolder(String accountId, HolderRequest request, String actor, String correlationId) {
        DepositAccount account = loadDetailed(accountId);
        ensureMutable(account);
        ensureTransactional(account);
        if (request.role() == HolderRole.PRIMARY) {
            throw new ApiException(HttpStatus.CONFLICT, "PRIMARY_HOLDER_ALREADY_EXISTS",
                    "Use a governed ownership-transfer workflow to replace the primary holder");
        }
        boolean exists = account.getHolders().stream().anyMatch(h -> h.getCustomerId().equals(request.customerId())
                && h.getStatus() == RecordStatus.ACTIVE);
        if (exists) throw new ApiException(HttpStatus.CONFLICT, "HOLDER_ALREADY_EXISTS", "Holder already exists");
        BankingReferenceGateway.ValidationResult validation = referenceGateway
                .validateCustomerEligibility(request.customerId());
        if (!validation.eligible()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "HOLDER_NOT_ELIGIBLE", "The customer is not eligible to hold this account");
        account.addHolder(new AccountHolder(UUID.randomUUID().toString(), request.customerId(), request.role(),
                request.authorizationType(), request.ownershipPercentage()));
        touch(account, actor);
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "ADD_HOLDER", "SUCCESS",
                actor, "USER", "HOLDER_ADDED", null, Hashing.sha256(request.customerId()), correlationId));
        return viewMapper.detail(account);
    }

    @Transactional
    public void removeHolder(String accountId, String customerId, String actor, String correlationId) {
        DepositAccount account = loadDetailed(accountId);
        ensureMutable(account);
        AccountHolder holder = account.getHolders().stream()
                .filter(h -> h.getCustomerId().equals(customerId) && h.getStatus() == RecordStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOLDER_NOT_FOUND", "Holder not found"));
        if (holder.getRole() == HolderRole.PRIMARY) throw new ApiException(HttpStatus.CONFLICT,
                "PRIMARY_HOLDER_REQUIRED", "The primary holder cannot be removed");
        holder.setStatus(RecordStatus.INACTIVE);
        holder.setRemovedAt(OffsetDateTime.now());
        touch(account, actor);
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "REMOVE_HOLDER", "SUCCESS",
                actor, "USER", "HOLDER_REMOVED", Hashing.sha256(customerId), null, correlationId));
    }

    @Transactional
    public List<NomineeView> replaceNominees(String accountId, List<NomineeRequest> requests,
                                             String actor, String correlationId) {
        DepositAccount account = loadDetailed(accountId);
        ensureMutable(account);
        if (!requests.isEmpty()) validateNominees(requests);
        nomineeRepository.deleteByAccountId(accountId);
        List<AccountNominee> saved = requests.stream().map(n -> nomineeRepository.save(new AccountNominee(
                UUID.randomUUID().toString(), accountId, n.customerReference(), piiProtector.encrypt(n.name()),
                n.relationshipCode(), n.allocationPercentage()))).toList();
        touch(account, actor);
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "REPLACE_NOMINEES", "SUCCESS",
                actor, "USER", "NOMINEES_REPLACED", null, Hashing.sha256(String.valueOf(saved.size())), correlationId));
        return saved.stream().map(n -> new NomineeView(n.getId(), n.getCustomerReference(), n.getRelationshipCode(),
                n.getAllocationPercentage(), n.getStatus().name())).toList();
    }

    @Transactional
    public LimitView upsertLimit(String accountId, LimitType pathType, LimitRequest request,
                                 String actor, String correlationId) {
        if (pathType != request.limitType()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "LIMIT_TYPE_MISMATCH", "Path and body limit types must match");
        DepositAccount account = loadDetailed(accountId);
        ensureMutable(account);
        ensureTransactional(account);
        if (!account.getCurrencyCode().equals(request.currency())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "CURRENCY_MISMATCH", "Limit currency must match the account currency");
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EFFECTIVE_DATES", "effectiveTo must be after effectiveFrom");
        }
        AccountLimit limit = limitRepository.findFirstByAccountIdAndLimitTypeOrderByEffectiveFromDesc(accountId, pathType)
                .orElseGet(() -> new AccountLimit(UUID.randomUUID().toString(), accountId, pathType, request.amount(),
                        request.currency(), request.effectiveFrom(), request.effectiveTo()));
        limit.setAmount(request.amount());
        limit.setCurrencyCode(request.currency());
        limit.setEffectiveFrom(request.effectiveFrom());
        limit.setEffectiveTo(request.effectiveTo());
        limitRepository.save(limit);
        touch(account, actor);
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "UPSERT_LIMIT", "SUCCESS",
                actor, "USER", pathType.name(), null, Hashing.sha256(request.amount().toPlainString()), correlationId));
        return viewMapper.limit(limit);
    }

    @Transactional
    public MandateView addMandate(String accountId, MandateRequest request, String actor, String correlationId) {
        DepositAccount account = loadDetailed(accountId);
        ensureMutable(account);
        ensureTransactional(account);
        if (request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANDATE_DATES", "validTo must be after validFrom");
        }
        BankingReferenceGateway.ValidationResult validation = referenceGateway
                .validateCustomerEligibility(request.authorizedCustomerId());
        if (!validation.eligible()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "MANDATE_PARTY_NOT_ELIGIBLE", "The authorized customer is not eligible");
        AccountMandate mandate = mandateRepository.save(new AccountMandate(UUID.randomUUID().toString(), accountId,
                request.authorizedCustomerId(), request.mandateType(), request.validFrom(), request.validTo()));
        touch(account, actor);
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "ADD_MANDATE", "SUCCESS",
                actor, "USER", request.mandateType(), null, Hashing.sha256(request.authorizedCustomerId()), correlationId));
        return new MandateView(mandate.getId(), mandate.getAuthorizedCustomerId(), mandate.getMandateType(),
                mandate.getStatus().name(), mandate.getValidFrom(), mandate.getValidTo());
    }

    @Transactional
    public void revokeMandate(String accountId, String mandateId, String actor, String correlationId) {
        DepositAccount account = loadDetailed(accountId);
        ensureTransactional(account);
        ensureMutable(account);
        AccountMandate mandate = mandateRepository.findById(mandateId).filter(m -> m.getAccountId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MANDATE_NOT_FOUND", "Mandate not found"));
        mandate.setStatus(RecordStatus.REVOKED);
        touch(account, actor);
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "REVOKE_MANDATE", "SUCCESS",
                actor, "USER", "MANDATE_REVOKED", Hashing.sha256(mandateId), null, correlationId));
    }

    @Transactional
    public AccountDetailView command(String accountId, String command, StatusCommand request,
                                     Long expectedVersion, String actor, String correlationId) {
        DepositAccount account = loadDetailed(accountId);
        ensureTransactional(account);
        if ("request-close".equals(command) || "confirm-close".equals(command)) {
            throw new ApiException(HttpStatus.CONFLICT, "USE_ACCOUNT_CLOSURE_WORKFLOW",
                    "Use the dedicated closure quote and closure request APIs");
        }
        if (expectedVersion != null && account.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, "STALE_ACCOUNT_VERSION",
                    "If-Match does not match the current account version");
        }
        AccountStatus from = account.getStatus();
        AccountStatus to = target(account, command);
        if ("confirm-close".equals(command)) assertCanClose(account);
        if (to == AccountStatus.FROZEN) account.setPreviousServiceableStatus(from);
        account.setStatus(to);
        if (to == AccountStatus.ACTIVE && account.getOpenedAt() == null) account.setOpenedAt(OffsetDateTime.now());
        if (to == AccountStatus.CLOSED) account.setClosedAt(OffsetDateTime.now());
        touch(account, actor);
        historyRepository.save(new AccountStatusHistory(UUID.randomUUID().toString(), accountId, from, to,
                request.reasonCode(), request.reasonText(), actor, "USER", correlationId));
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), accountId, "STATUS_" + command.toUpperCase(),
                "SUCCESS", actor, "USER", request.reasonCode(), Hashing.sha256(from.name()),
                Hashing.sha256(to.name()), correlationId));
        return viewMapper.detail(account);
    }

    @Transactional(readOnly = true)
    public AccountEligibilityView internalEligibility(String accountId) {
        DepositAccount account = loadDetailed(accountId);
        boolean fixedDeposit = account.getProductSubtype() == ProductSubtype.FIXED_DEPOSIT;
        boolean debit = !fixedDeposit && account.getStatus() == AccountStatus.ACTIVE;
        boolean credit = !fixedDeposit && (account.getStatus() == AccountStatus.ACTIVE || account.getStatus() == AccountStatus.BLOCKED);
        List<LimitView> limits = limitRepository.findByAccountId(accountId).stream().map(viewMapper::limit).toList();
        return new AccountEligibilityView(accountId, account.getStatus(), debit, credit,
                account.getCurrencyCode(), limits, OffsetDateTime.now());
    }

    private AccountStatus target(DepositAccount account, String command) {
        AccountStatus current = account.getStatus();
        return switch (command) {
            case "activate" -> requireTransition(current, AccountStatus.PENDING_ACTIVATION, AccountStatus.ACTIVE);
            case "block" -> requireTransition(current, AccountStatus.ACTIVE, AccountStatus.BLOCKED);
            case "unblock" -> requireTransition(current, AccountStatus.BLOCKED, AccountStatus.ACTIVE);
            case "freeze" -> {
                if (current != AccountStatus.ACTIVE && current != AccountStatus.BLOCKED && current != AccountStatus.DORMANT)
                    invalidTransition(current, command);
                yield AccountStatus.FROZEN;
            }
            case "release-freeze" -> {
                if (current != AccountStatus.FROZEN) invalidTransition(current, command);
                yield account.getPreviousServiceableStatus() == null ? AccountStatus.ACTIVE : account.getPreviousServiceableStatus();
            }
            case "mark-dormant" -> requireTransition(current, AccountStatus.ACTIVE, AccountStatus.DORMANT);
            case "reactivate" -> requireTransition(current, AccountStatus.DORMANT, AccountStatus.ACTIVE);
            case "request-close" -> {
                if (current != AccountStatus.ACTIVE && current != AccountStatus.BLOCKED && current != AccountStatus.DORMANT)
                    invalidTransition(current, command);
                yield AccountStatus.CLOSURE_PENDING;
            }
            case "confirm-close" -> requireTransition(current, AccountStatus.CLOSURE_PENDING, AccountStatus.CLOSED);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMMAND", "Unsupported account command");
        };
    }

    private AccountStatus requireTransition(AccountStatus current, AccountStatus expected, AccountStatus target) {
        if (current != expected) invalidTransition(current, target.name());
        return target;
    }

    private void invalidTransition(AccountStatus current, String command) {
        throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                "Command " + command + " is not allowed from " + current);
    }

    private void assertCanClose(DepositAccount account) {
        AccountBalance b = account.getBalance();
        AccountingBalanceGateway.AccountBalanceResult authoritative = accountingBalanceGateway.getBalance(account.getId());
        if (b == null || authoritative.ledgerBalance() == null || authoritative.ledgerBalance().signum() != 0
                || !account.getCurrencyCode().equals(authoritative.currency()) || b.getAvailableBalance().signum() != 0
                || b.getBlockedAmount().signum() != 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLOSURE_CHECK_FAILED",
                    "Account cannot close while balances or holds are non-zero");
        }
    }

    private void validateOpeningRequest(OpenDepositAccountRequest request) {
        if (!request.customerIds().contains(request.primaryCustomerId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRIMARY_HOLDER_MISSING",
                    "primaryCustomerId must be included in customerIds");
        }
        if (new HashSet<>(request.customerIds()).size() != request.customerIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_HOLDER", "customerIds must be unique");
        }
        if (request.nominees() != null && !request.nominees().isEmpty()) validateNominees(request.nominees());
    }

    private void validateNominees(List<NomineeRequest> nominees) {
        BigDecimal total = nominees.stream().map(NomineeRequest::allocationPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NOMINEE_ALLOCATION",
                    "Nominee allocations must total 100 percent");
        }
    }

    private DepositAccount loadDetailed(String accountId) {
        return accountRepository.findDetailedById(accountId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Deposit account not found"));
    }

    private void ensureExists(String accountId) {
        if (!accountRepository.existsById(accountId)) throw new ApiException(HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND", "Deposit account not found");
    }

    private void ensureMutable(DepositAccount account) {
        if (account.getStatus() == AccountStatus.CLOSED || account.getStatus() == AccountStatus.CLOSURE_PENDING)
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_MUTABLE", "Account is closing or closed");
    }

    private void ensureTransactional(DepositAccount account) {
        if (account.getProductSubtype() == ProductSubtype.FIXED_DEPOSIT) {
            throw new ApiException(HttpStatus.CONFLICT, "FIXED_DEPOSIT_OPERATION_NOT_ALLOWED",
                    "This operation is not available for fixed-deposit accounts");
        }
    }

    private void touch(DepositAccount account, String actor) {
        account.setUpdatedAt(OffsetDateTime.now());
        account.setUpdatedBy(actor);
    }
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize request/event", ex);
        }
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required and must be at most 128 characters");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    public String currentCorrelationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElseGet(() -> UUID.randomUUID().toString());
    }
}
