package com.moneybags.accounting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "TRIAL_BALANCE_LINE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrialBalanceLine {
    @Id @Column(name = "LINE_ID", length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "RUN_ID") private TrialBalanceRun run;
    @Column(name = "GL_CODE", length = 40, nullable = false) private String glCode;
    @Column(name = "DEBIT_TOTAL", precision = 19, scale = 4, nullable = false) private BigDecimal debitTotal;
    @Column(name = "CREDIT_TOTAL", precision = 19, scale = 4, nullable = false) private BigDecimal creditTotal;
    @Column(name = "CLOSING_BALANCE", precision = 19, scale = 4, nullable = false) private BigDecimal closingBalance;

    public TrialBalanceLine(String id, String glCode, BigDecimal debitTotal, BigDecimal creditTotal,
                            BigDecimal closingBalance) {
        this.id = id; this.glCode = glCode; this.debitTotal = debitTotal;
        this.creditTotal = creditTotal; this.closingBalance = closingBalance;
    }
    void attach(TrialBalanceRun run) { this.run = run; }
}
