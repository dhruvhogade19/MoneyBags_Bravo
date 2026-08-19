package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ACCT_FIN_RECON_RUN")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialReconciliationRun {
    @Id @Column(name = "RUN_ID", length = 36) private String id;
    @Column(name = "EOD_RUN_ID", length = 80, nullable = false) private String eodRunId;
    @Column(name = "BUSINESS_DATE", nullable = false) private LocalDate businessDate;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Column(name = "EXPECTED_COUNT", nullable = false) private long expectedCount;
    @Column(name = "ACTUAL_COUNT", nullable = false) private long actualCount;
    @Column(name = "EXPECTED_TOTAL", precision = 19, scale = 4, nullable = false) private BigDecimal expectedTotal;
    @Column(name = "ACTUAL_TOTAL", precision = 19, scale = 4, nullable = false) private BigDecimal actualTotal;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private ReconciliationStatus status;
    @Column(name = "CREATED_AT", nullable = false) private OffsetDateTime createdAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private long version;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FinancialReconciliationItem> items = new ArrayList<>();

    public FinancialReconciliationRun(String id, String eodRunId, LocalDate businessDate, String currencyCode,
                                      long expectedCount, long actualCount, BigDecimal expectedTotal,
                                      BigDecimal actualTotal) {
        this.id = id; this.eodRunId = eodRunId; this.businessDate = businessDate; this.currencyCode = currencyCode;
        this.expectedCount = expectedCount; this.actualCount = actualCount; this.expectedTotal = expectedTotal;
        this.actualTotal = actualTotal; this.status = expectedCount == actualCount && expectedTotal.compareTo(actualTotal) == 0
                ? ReconciliationStatus.MATCHED : ReconciliationStatus.EXCEPTION;
        this.createdAt = OffsetDateTime.now();
    }

    public void addItem(FinancialReconciliationItem item) { items.add(item); item.attach(this); }
    public void markResolvedIfComplete() {
        if (!items.isEmpty() && items.stream().noneMatch(FinancialReconciliationItem::isOpen)) {
            status = ReconciliationStatus.RESOLVED;
        }
    }
}
