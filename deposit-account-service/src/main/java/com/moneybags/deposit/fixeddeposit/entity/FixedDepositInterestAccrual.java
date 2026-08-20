package com.moneybags.deposit.fixeddeposit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "FD_INTEREST_ACCRUAL", uniqueConstraints =
        @UniqueConstraint(name = "UQ_FD_ACCRUAL_DATE", columnNames = {"FD_ID", "BUSINESS_DATE"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FixedDepositInterestAccrual {
    public static final String ACCOUNTING_LEGACY_ACCEPTED = "LEGACY_ACCEPTED";
    public static final String ACCOUNTING_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    @Id @Column(name = "ACCRUAL_ID", length = 36) private String id;
    @Column(name = "FD_ID", length = 36, nullable = false) private String fixedDepositId;
    @Column(name = "BUSINESS_DATE", nullable = false) private LocalDate businessDate;
    @Column(name = "ACCRUAL_BASE", precision = 19, scale = 4, nullable = false) private BigDecimal accrualBase;
    @Column(name = "ANNUAL_RATE", precision = 12, scale = 8, nullable = false) private BigDecimal annualRate;
    @Column(name = "DAY_COUNT_BASIS", nullable = false) private int dayCountBasis;
    @Column(name = "INTEREST_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal interestAmount;
    @Column(name = "CUMULATIVE_INTEREST", precision = 19, scale = 4, nullable = false) private BigDecimal cumulativeInterest;
    @Column(name = "STATUS", length = 20, nullable = false) private String status;
    @Column(name = "SOURCE_REFERENCE", length = 100, nullable = false) private String sourceReference;
    @Column(name = "ACCOUNTING_JOURNAL_NUMBER", length = 100) private String accountingJournalNumber;
    @Column(name = "ACCOUNTING_POSTING_STATUS", length = 20) private String accountingPostingStatus;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public FixedDepositInterestAccrual(String id, String fdId, LocalDate date, BigDecimal base, BigDecimal rate,
                                       BigDecimal amount, BigDecimal cumulative, String sourceReference) {
        this.id=id; this.fixedDepositId=fdId; this.businessDate=date; this.accrualBase=base; this.annualRate=rate;
        this.dayCountBasis=365; this.interestAmount=amount; this.cumulativeInterest=cumulative;
        this.status="CALCULATED"; this.sourceReference=sourceReference; this.createdAt=OffsetDateTime.now();
    }

    public void recordAccountingPosting(String journalNumber, String postingStatus) {
        this.accountingJournalNumber = journalNumber;
        this.accountingPostingStatus = postingStatus;
        this.status = "POSTED";
    }

    public void recordLegacyAccountingAcceptance() {
        this.accountingJournalNumber = null;
        this.accountingPostingStatus = ACCOUNTING_LEGACY_ACCEPTED;
    }

    public void recordAccountingReviewRequired() {
        this.accountingPostingStatus = ACCOUNTING_REVIEW_REQUIRED;
    }

    public boolean hasNoAccountingDisposition() {
        return (accountingJournalNumber == null || accountingJournalNumber.isBlank())
                && (accountingPostingStatus == null || accountingPostingStatus.isBlank());
    }

    public boolean needsAccountingPosting() {
        if (ACCOUNTING_LEGACY_ACCEPTED.equalsIgnoreCase(accountingPostingStatus)
                || ACCOUNTING_REVIEW_REQUIRED.equalsIgnoreCase(accountingPostingStatus)) return false;
        return accountingJournalNumber == null || accountingJournalNumber.isBlank()
                || !"POSTED".equalsIgnoreCase(accountingPostingStatus);
    }
}
