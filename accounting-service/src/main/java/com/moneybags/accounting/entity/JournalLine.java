package com.moneybags.accounting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "JOURNAL_LINE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalLine {
    @Id @Column(name = "LINE_ID", length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "JOURNAL_ID") private Journal journal;
    @Column(name = "LINE_NUMBER", nullable = false) private int lineNumber;
    @Column(name = "GL_CODE", length = 40, nullable = false) private String glCode;
    @Column(name = "SUBLEDGER_REFERENCE", length = 100) private String subledgerReference;
    @Column(name = "COMPONENT_TYPE", length = 40, nullable = false) private String componentType;
    @Column(name = "RULE_CODE", length = 60, nullable = false) private String ruleCode;
    @Column(name = "RULE_VERSION", nullable = false) private int ruleVersion;
    @Column(name = "DEBIT_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal debitAmount;
    @Column(name = "CREDIT_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal creditAmount;
    @Column(name = "NARRATION", length = 500) private String narration;

    public JournalLine(String id, int lineNumber, String glCode, String subledgerReference, String componentType,
                       String ruleCode, int ruleVersion, BigDecimal debitAmount, BigDecimal creditAmount,
                       String narration) {
        this.id = id; this.lineNumber = lineNumber; this.glCode = glCode;
        this.subledgerReference = subledgerReference; this.componentType = componentType;
        this.ruleCode = ruleCode; this.ruleVersion = ruleVersion; this.debitAmount = debitAmount;
        this.creditAmount = creditAmount; this.narration = narration;
    }

    void attach(Journal journal) { this.journal = journal; }
}
