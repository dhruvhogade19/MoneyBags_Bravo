package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.JournalStatus;
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
@Table(name = "ACCT_JOURNAL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Journal {
    @Id @Column(name = "JOURNAL_ID", length = 36) private String id;
    @Column(name = "JOURNAL_NUMBER", length = 60, nullable = false, unique = true) private String journalNumber;
    @Column(name = "POSTING_SEQUENCE", nullable = false, unique = true) private long postingSequence;
    @Column(name = "EXTERNAL_REFERENCE", length = 160, nullable = false, unique = true) private String externalReference;
    @Column(name = "SOURCE_SERVICE", length = 80, nullable = false) private String sourceService;
    @Column(name = "EVENT_TYPE", length = 60, nullable = false) private String eventType;
    @Column(name = "OCCURRED_AT", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "BUSINESS_DATE", nullable = false) private LocalDate businessDate;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private JournalStatus status;
    @Column(name = "TOTAL_DEBIT", precision = 19, scale = 4, nullable = false) private BigDecimal totalDebit;
    @Column(name = "TOTAL_CREDIT", precision = 19, scale = 4, nullable = false) private BigDecimal totalCredit;
    @Column(name = "REVERSES_JOURNAL_NUMBER", length = 60) private String reversesJournalNumber;
    @Column(name = "CORRELATION_ID", length = 64, nullable = false) private String correlationId;
    @Column(name = "POSTED_AT", nullable = false) private OffsetDateTime postedAt;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<JournalLine> lines = new ArrayList<>();

    public Journal(String id, String journalNumber, long postingSequence, String externalReference,
                   String sourceService, String eventType, OffsetDateTime occurredAt, LocalDate businessDate,
                   String currencyCode, BigDecimal totalDebit, BigDecimal totalCredit,
                   String reversesJournalNumber, String correlationId) {
        this.id = id; this.journalNumber = journalNumber; this.postingSequence = postingSequence;
        this.externalReference = externalReference; this.sourceService = sourceService; this.eventType = eventType;
        this.occurredAt = occurredAt; this.businessDate = businessDate; this.currencyCode = currencyCode;
        this.status = JournalStatus.POSTED; this.totalDebit = totalDebit; this.totalCredit = totalCredit;
        this.reversesJournalNumber = reversesJournalNumber; this.correlationId = correlationId;
        this.postedAt = OffsetDateTime.now();
    }

    public void addLine(JournalLine line) {
        lines.add(line); line.attach(this);
    }
}
