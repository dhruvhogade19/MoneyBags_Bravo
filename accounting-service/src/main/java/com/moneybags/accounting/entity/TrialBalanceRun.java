package com.moneybags.accounting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.NumericBooleanConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ACCT_TRIAL_BALANCE_RUN")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrialBalanceRun {
    @Id @Column(name = "RUN_ID", length = 36) private String id;
    @Column(name = "BUSINESS_DATE", nullable = false) private LocalDate businessDate;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Column(name = "TOTAL_DEBIT", precision = 19, scale = 4, nullable = false) private BigDecimal totalDebit;
    @Column(name = "TOTAL_CREDIT", precision = 19, scale = 4, nullable = false) private BigDecimal totalCredit;
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "BALANCED_FLAG", nullable = false, columnDefinition = "NUMBER(1)") private boolean balanced;
    @Column(name = "GENERATED_BY", length = 100, nullable = false) private String generatedBy;
    @Column(name = "GENERATED_AT", nullable = false) private OffsetDateTime generatedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("glCode ASC") private List<TrialBalanceLine> lines = new ArrayList<>();

    public TrialBalanceRun(String id, LocalDate businessDate, String currencyCode, BigDecimal totalDebit,
                           BigDecimal totalCredit, String generatedBy) {
        this.id = id; this.businessDate = businessDate; this.currencyCode = currencyCode;
        this.totalDebit = totalDebit; this.totalCredit = totalCredit;
        this.balanced = totalDebit.compareTo(totalCredit) == 0; this.generatedBy = generatedBy;
        this.generatedAt = OffsetDateTime.now();
    }

    public void addLine(TrialBalanceLine line) { lines.add(line); line.attach(this); }
}
