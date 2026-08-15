package com.moneybags.accounting.repository;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import com.moneybags.accounting.entity.AccountingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountingRuleRepository extends JpaRepository<AccountingRule, String> {
    List<AccountingRule> findByEventTypeAndComponentTypeAndCurrencyCodeAndStatus(
            String eventType, String componentType, String currencyCode, RecordStatus status);
    boolean existsByRuleCodeAndRuleVersion(String ruleCode, int ruleVersion);
}
