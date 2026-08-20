package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCT_SUBLEDGER_MAPPING")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubledgerMapping {
    @Id @Column(name = "MAPPING_ID", length = 36) private String id;
    @Column(name = "MAPPING_CODE", length = 60, nullable = false) private String mappingCode;
    @Column(name = "PRODUCT_CODE", length = 40) private String productCode;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Column(name = "GL_CODE", length = 40, nullable = false) private String glCode;
    @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private RecordStatus status;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public SubledgerMapping(String id, String mappingCode, String productCode, String currencyCode, String glCode,
                            LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.id = id; this.mappingCode = mappingCode; this.productCode = productCode; this.currencyCode = currencyCode;
        this.glCode = glCode; this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo;
        this.status = RecordStatus.ACTIVE; this.createdAt = OffsetDateTime.now();
    }

    public boolean applies(String product, LocalDate date) {
        boolean productMatches = productCode == null || productCode.equals(product);
        return status == RecordStatus.ACTIVE && productMatches && !effectiveFrom.isAfter(date)
                && (effectiveTo == null || !effectiveTo.isBefore(date));
    }
}
