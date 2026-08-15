package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNTING_RULE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountingRule {
    @Id @Column(name = "RULE_ID", length = 36) private String id;
    @Column(name = "RULE_CODE", length = 60, nullable = false) private String ruleCode;
    @Column(name = "EVENT_TYPE", length = 60, nullable = false) private String eventType;
    @Column(name = "COMPONENT_TYPE", length = 40, nullable = false) private String componentType;
    @Column(name = "PRODUCT_CODE", length = 40) private String productCode;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Column(name = "RULE_VERSION", nullable = false) private int ruleVersion;
    @Column(name = "DEBIT_MAPPING_CODE", length = 60, nullable = false) private String debitMappingCode;
    @Column(name = "CREDIT_MAPPING_CODE", length = 60, nullable = false) private String creditMappingCode;
    @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private RecordStatus status;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public AccountingRule(String id, String ruleCode, String eventType, String componentType, String productCode,
                          String currencyCode, int ruleVersion, String debitMappingCode, String creditMappingCode,
                          LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.id = id; this.ruleCode = ruleCode; this.eventType = eventType; this.componentType = componentType;
        this.productCode = productCode; this.currencyCode = currencyCode; this.ruleVersion = ruleVersion;
        this.debitMappingCode = debitMappingCode; this.creditMappingCode = creditMappingCode;
        this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo; this.status = RecordStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
    }

    public boolean applies(String product, LocalDate date) {
        boolean productMatches = productCode == null || productCode.equals(product);
        return status == RecordStatus.ACTIVE && productMatches && !effectiveFrom.isAfter(date)
                && (effectiveTo == null || !effectiveTo.isBefore(date));
    }
}
