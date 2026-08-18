package com.moneybags.accounting.repository;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import com.moneybags.accounting.entity.AccountingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AccountingRuleRepository extends JpaRepository<AccountingRule, String> {
    List<AccountingRule> findByEventTypeAndComponentTypeAndCurrencyCodeAndStatus(
            String eventType, String componentType, String currencyCode, RecordStatus status);
    boolean existsByRuleCodeAndRuleVersion(String ruleCode, int ruleVersion);
    @Query("select r from AccountingRule r where (:search is null or lower(r.ruleCode) like lower(concat('%',:search,'%')) " +
            "or lower(r.eventType) like lower(concat('%',:search,'%'))) and (:eventType is null or r.eventType=:eventType) " +
            "and (:status is null or r.status=:status) and (:currency is null or r.currencyCode=:currency)")
    Page<AccountingRule> search(@Param("search") String search, @Param("eventType") String eventType,
                                @Param("status") RecordStatus status, @Param("currency") String currency,
                                Pageable pageable);
}
