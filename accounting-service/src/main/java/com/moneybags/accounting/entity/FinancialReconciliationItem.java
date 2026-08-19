package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.ReconciliationItemStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.NumericBooleanConverter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCT_FIN_RECON_ITEM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialReconciliationItem {
    @Id @Column(name = "ITEM_ID", length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "RUN_ID") private FinancialReconciliationRun run;
    @Column(name = "REFERENCE", length = 160, nullable = false) private String reference;
    @Column(name = "EXPECTED_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal expectedAmount;
    @Column(name = "ACTUAL_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal actualAmount;
    @Column(name = "DIFFERENCE", precision = 19, scale = 4, nullable = false) private BigDecimal difference;
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "BLOCKING_FLAG", nullable = false, columnDefinition = "NUMBER(1)") private boolean blocking;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private ReconciliationItemStatus status;
    @Column(name = "RESOLUTION", length = 1000) private String resolution;
    @Column(name = "RESOLVED_BY", length = 100) private String resolvedBy;
    @Column(name = "RESOLVED_AT") private OffsetDateTime resolvedAt;

    public FinancialReconciliationItem(String id, String reference, BigDecimal expectedAmount,
                                       BigDecimal actualAmount, boolean blocking) {
        this.id = id; this.reference = reference; this.expectedAmount = expectedAmount; this.actualAmount = actualAmount;
        this.difference = actualAmount.subtract(expectedAmount); this.blocking = blocking;
        this.status = ReconciliationItemStatus.OPEN;
    }
    void attach(FinancialReconciliationRun run) { this.run = run; }
    public boolean isOpen() { return status == ReconciliationItemStatus.OPEN; }
    public void resolve(ReconciliationItemStatus status, String resolution, String actor) {
        this.status = status; this.resolution = resolution; this.resolvedBy = actor; this.resolvedAt = OffsetDateTime.now();
    }
}
