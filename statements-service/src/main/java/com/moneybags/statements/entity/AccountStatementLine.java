package com.moneybags.statements.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_STATEMENT_LINE")
public class AccountStatementLine {
    @Id @Column(name = "LINE_ID", length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "STATEMENT_ID", nullable = false)
    private AccountStatement statement;
    @Column(name = "LINE_SEQUENCE", nullable = false) private int sequence;
    @Column(name = "TRANSACTION_ID", length = 64) private String transactionId;
    @Column(name = "PAYMENT_ID", length = 64) private String paymentId;
    @Column(name = "OCCURRED_AT", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "DESCRIPTION", length = 500, nullable = false) private String description;
    @Column(name = "DEBIT_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal debit;
    @Column(name = "CREDIT_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal credit;
    @Column(name = "BALANCE_AFTER", precision = 19, scale = 4, nullable = false) private BigDecimal balanceAfter;
    @Column(name = "JOURNAL_NUMBER", length = 60) private String journalNumber;

    protected AccountStatementLine() {}
    public AccountStatementLine(String id, AccountStatement statement, int sequence,
            String transactionId, String paymentId, OffsetDateTime occurredAt,
            String description, BigDecimal debit, BigDecimal credit, BigDecimal balanceAfter, String journalNumber) {
        this.id = id; this.statement = statement; this.sequence = sequence; this.occurredAt = occurredAt;
        this.transactionId = transactionId; this.paymentId = paymentId;
        this.description = description; this.debit = debit; this.credit = credit; this.balanceAfter = balanceAfter;
        this.journalNumber = journalNumber;
    }
    public int getSequence() { return sequence; } public String getTransactionId() { return transactionId; }
    public String getPaymentId() { return paymentId; } public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getDescription() { return description; } public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; } public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getJournalNumber() { return journalNumber; }
}
