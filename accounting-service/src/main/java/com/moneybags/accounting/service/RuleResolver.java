package com.moneybags.accounting.service;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import com.moneybags.accounting.entity.AccountingRule;
import com.moneybags.accounting.entity.GlAccount;
import com.moneybags.accounting.entity.SubledgerMapping;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.AccountingRuleRepository;
import com.moneybags.accounting.repository.GlAccountRepository;
import com.moneybags.accounting.repository.SubledgerMappingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;

@Service
public class RuleResolver {
    public record ResolvedRule(String ruleCode, int ruleVersion, String debitGlCode, String creditGlCode) {}
    private final AccountingRuleRepository rules;
    private final SubledgerMappingRepository mappings;
    private final GlAccountRepository glAccounts;

    public RuleResolver(AccountingRuleRepository rules, SubledgerMappingRepository mappings,
                        GlAccountRepository glAccounts) {
        this.rules = rules; this.mappings = mappings; this.glAccounts = glAccounts;
    }

    @Transactional(readOnly = true)
    public ResolvedRule resolve(String eventType, String componentType, String productCode,
                                String currency, LocalDate date) {
        AccountingRule rule = rules.findByEventTypeAndComponentTypeAndCurrencyCodeAndStatus(
                        eventType, componentType, currency, RecordStatus.ACTIVE).stream()
                .filter(value -> value.applies(productCode, date))
                .sorted(Comparator.comparing((AccountingRule value) -> value.getProductCode() == null ? 1 : 0)
                        .thenComparing(AccountingRule::getRuleVersion, Comparator.reverseOrder()))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "ACCOUNTING_RULE_NOT_CONFIGURED", "No active accounting rule is configured for "
                        + eventType + "/" + componentType + "/" + currency));
        String debit = resolveMapping(rule.getDebitMappingCode(), productCode, currency, date);
        String credit = resolveMapping(rule.getCreditMappingCode(), productCode, currency, date);
        return new ResolvedRule(rule.getRuleCode(), rule.getRuleVersion(), debit, credit);
    }

    @Transactional(readOnly = true)
    public String resolveMapping(String mappingCode, String productCode, String currency, LocalDate date) {
        SubledgerMapping mapping = mappings.findByMappingCodeAndCurrencyCodeAndStatus(
                        mappingCode, currency, RecordStatus.ACTIVE).stream()
                .filter(value -> value.applies(productCode, date))
                .sorted(Comparator.comparing((SubledgerMapping value) -> value.getProductCode() == null ? 1 : 0))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "SUBLEDGER_MAPPING_NOT_CONFIGURED", "No active mapping is configured for " + mappingCode));
        GlAccount gl = glAccounts.findByGlCode(mapping.getGlCode()).orElseThrow(() -> new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, "GL_ACCOUNT_NOT_FOUND", "Mapped GL account does not exist"));
        if (gl.getStatus() != RecordStatus.ACTIVE) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "GL_ACCOUNT_INACTIVE", "Mapped GL account is inactive: " + gl.getGlCode());
        return gl.getGlCode();
    }
}
