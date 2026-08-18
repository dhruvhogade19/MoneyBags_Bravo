package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.domain.DomainTypes.GlAccountType;
import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import com.moneybags.accounting.entity.*;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.*;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConfigurationService {
    private final GlAccountRepository glAccounts;
    private final AccountingRuleRepository rules;
    private final SubledgerMappingRepository mappings;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public ConfigurationService(GlAccountRepository glAccounts, AccountingRuleRepository rules,
                                SubledgerMappingRepository mappings, IdempotencyService idempotency,
                                AuditService audit) {
        this.glAccounts = glAccounts; this.rules = rules; this.mappings = mappings;
        this.idempotency = idempotency; this.audit = audit;
    }

    public GlAccountResponse createGl(GlAccountRequest request, String key, String actor) {
        return idempotency.execute("GL_ACCOUNT_CREATE", key, request, GlAccountResponse.class,
                () -> createGlInternal(request, actor));
    }

    @Transactional
    GlAccountResponse createGlInternal(GlAccountRequest request, String actor) {
        if (glAccounts.existsByGlCode(request.glCode())) throw new ApiException(HttpStatus.CONFLICT,
                "GL_ACCOUNT_EXISTS", "GL account already exists: " + request.glCode());
        if (request.parentGlCode() != null && !glAccounts.existsByGlCode(request.parentGlCode()))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARENT_GL_NOT_FOUND",
                    "Parent GL account does not exist");
        GlAccount value = glAccounts.save(new GlAccount(UUID.randomUUID().toString(), request.glCode(),
                request.name(), request.accountType(), request.normalBalance(), request.currencyCode(),
                request.parentGlCode()));
        audit.record(value.getGlCode(), "CREATE_GL_ACCOUNT", "SUCCESS", actor, "USER", correlation());
        return gl(value);
    }

    @Transactional(readOnly = true)
    public GlAccountResponse getGl(String glCode) { return gl(loadGl(glCode)); }

    @Transactional(readOnly = true)
    public GlAccountPage listGl(String search, GlAccountType accountType, RecordStatus status, String currency,
                                int page, int size) {
        Page<GlAccount> values = glAccounts.search(blankToNull(search), accountType, status,
                blankToNull(currency), PageRequest.of(page, size, Sort.by("glCode")));
        return new GlAccountPage(values.map(this::gl).getContent(), page, size, values.getTotalElements(),
                values.getTotalPages());
    }

    public GlAccountResponse changeStatus(String glCode, StatusChangeRequest request, long expectedVersion,
                                          String key, String actor) {
        return idempotency.execute("GL_ACCOUNT_STATUS:" + glCode, key,
                new Object[] {request, expectedVersion}, GlAccountResponse.class,
                () -> changeStatusInternal(glCode, request, expectedVersion, actor));
    }

    @Transactional
    GlAccountResponse changeStatusInternal(String glCode, StatusChangeRequest request, long expectedVersion,
                                           String actor) {
        GlAccount value = loadGl(glCode);
        if (value.getVersion() != expectedVersion) throw new ApiException(HttpStatus.PRECONDITION_FAILED,
                "STALE_RESOURCE_VERSION", "If-Match does not match the current GL account version");
        value.changeStatus(request.status());
        glAccounts.saveAndFlush(value);
        audit.record(glCode, "CHANGE_GL_STATUS", "SUCCESS", actor, "USER", correlation());
        return gl(value);
    }

    public AccountingRuleResponse createRule(AccountingRuleRequest request, String key, String actor) {
        return idempotency.execute("ACCOUNTING_RULE_CREATE", key, request, AccountingRuleResponse.class,
                () -> createRuleInternal(request, actor));
    }

    @Transactional
    AccountingRuleResponse createRuleInternal(AccountingRuleRequest request, String actor) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EFFECTIVE_DATES",
                    "effectiveTo must not be before effectiveFrom");
        if (rules.existsByRuleCodeAndRuleVersion(request.ruleCode(), request.version()))
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNTING_RULE_VERSION_EXISTS",
                    "The accounting rule version already exists");
        requireMapping(request.debitMappingCode(), request.currencyCode());
        requireMapping(request.creditMappingCode(), request.currencyCode());
        AccountingRule value = rules.save(new AccountingRule(UUID.randomUUID().toString(), request.ruleCode(),
                request.eventType(), request.componentType(), request.productCode(), request.currencyCode(),
                request.version(), request.debitMappingCode(), request.creditMappingCode(),
                request.effectiveFrom(), request.effectiveTo()));
        audit.record(value.getRuleCode(), "CREATE_ACCOUNTING_RULE", "SUCCESS", actor, "USER", correlation());
        return rule(value);
    }

    @Transactional(readOnly = true)
    public AccountingRulePage listRules(String search, String eventType, RecordStatus status, String currency,
                                         int page, int size) {
        Page<AccountingRule> values = rules.search(blankToNull(search), blankToNull(eventType), status,
                blankToNull(currency), PageRequest.of(page, size,
                Sort.by("ruleCode").ascending().and(Sort.by("ruleVersion").descending())));
        return new AccountingRulePage(values.map(this::rule).getContent(), page, size,
                values.getTotalElements(), values.getTotalPages());
    }

    public SubledgerMappingResponse createMapping(SubledgerMappingRequest request, String key, String actor) {
        return idempotency.execute("SUBLEDGER_MAPPING_CREATE", key, request, SubledgerMappingResponse.class,
                () -> createMappingInternal(request, actor));
    }

    @Transactional
    SubledgerMappingResponse createMappingInternal(SubledgerMappingRequest request, String actor) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EFFECTIVE_DATES",
                    "effectiveTo must not be before effectiveFrom");
        GlAccount gl = loadGl(request.glCode());
        if (!gl.getCurrencyCode().equals(request.currencyCode())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "GL_CURRENCY_MISMATCH", "Mapping currency does not match the GL currency");
        SubledgerMapping value = mappings.save(new SubledgerMapping(UUID.randomUUID().toString(),
                request.mappingCode(), request.productCode(), request.currencyCode(), request.glCode(),
                request.effectiveFrom(), request.effectiveTo()));
        audit.record(value.getMappingCode(), "CREATE_SUBLEDGER_MAPPING", "SUCCESS", actor, "USER", correlation());
        return mapping(value);
    }

    @Transactional(readOnly = true)
    public SubledgerMappingPage listMappings(String search, String glCode, RecordStatus status, String currency,
                                              int page, int size) {
        Page<SubledgerMapping> values = mappings.search(blankToNull(search), blankToNull(glCode), status,
                blankToNull(currency), PageRequest.of(page, size, Sort.by("mappingCode")));
        return new SubledgerMappingPage(values.map(this::mapping).getContent(), page, size,
                values.getTotalElements(), values.getTotalPages());
    }

    private GlAccount loadGl(String glCode) {
        return glAccounts.findByGlCode(glCode).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "GL_ACCOUNT_NOT_FOUND", "GL account not found: " + glCode));
    }
    private void requireMapping(String code, String currency) {
        if (mappings.findByMappingCodeAndCurrencyCodeAndStatus(code, currency,
                com.moneybags.accounting.domain.DomainTypes.RecordStatus.ACTIVE).isEmpty())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SUBLEDGER_MAPPING_NOT_FOUND",
                    "Active subledger mapping not found: " + code);
    }
    private String correlation() { return MDC.get("correlationId") == null ? "unknown" : MDC.get("correlationId"); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private GlAccountResponse gl(GlAccount value) { return new GlAccountResponse(value.getGlCode(), value.getName(),
            value.getAccountType(), value.getNormalBalance(), value.getCurrencyCode(), value.getParentGlCode(),
            value.getStatus(), value.getVersion()); }
    private AccountingRuleResponse rule(AccountingRule value) { return new AccountingRuleResponse(value.getRuleCode(),
            value.getEventType(), value.getComponentType(), value.getProductCode(), value.getCurrencyCode(),
            value.getRuleVersion(), value.getDebitMappingCode(), value.getCreditMappingCode(),
            value.getEffectiveFrom(), value.getEffectiveTo(), value.getStatus()); }
    private SubledgerMappingResponse mapping(SubledgerMapping value) { return new SubledgerMappingResponse(
            value.getMappingCode(), value.getProductCode(), value.getGlCode(), value.getCurrencyCode(),
            value.getEffectiveFrom(), value.getEffectiveTo(), value.getStatus()); }
}
