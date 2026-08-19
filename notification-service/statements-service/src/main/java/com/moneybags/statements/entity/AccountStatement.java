package com.moneybags.statements.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_STATEMENT", uniqueConstraints = @UniqueConstraint(name = "UK_STATEMENT_PERIOD",
        columnNames = {"ACCOUNT_REFERENCE", "PERIOD_START", "PERIOD_END", "ACCOUNT_TYPE"}))
public class AccountStatement {
    @Id @Column(name = "STATEMENT_ID", length = 36) private String id;
    @Column(name = "CIF_ID", length = 100, nullable = false) private String cifId;
    @Column(name = "ACCOUNT_REFERENCE", length = 100, nullable = false) private String accountReference;
    @Column(name = "ACCOUNT_TYPE", length = 40, nullable = false) private String accountType;
    @Column(name = "MASKED_ACCOUNT_REFERENCE", length = 100, nullable = false) private String maskedAccountReference;
    @Column(name = "PERIOD_START", nullable = false) private LocalDate periodStart;
    @Column(name = "PERIOD_END", nullable = false) private LocalDate periodEnd;
    @Column(name = "CURRENCY_CODE", length = 3, nullable = false) private String currency;
    @Column(name = "OPENING_BALANCE", precision = 19, scale = 4, nullable = false) private BigDecimal openingBalance;
    @Column(name = "CLOSING_BALANCE", precision = 19, scale = 4, nullable = false) private BigDecimal closingBalance;
    @Column(name = "GENERATED_AT", nullable = false) private OffsetDateTime generatedAt;
    @Column(name = "STATUS", length = 20, nullable = false) private String status;
    @Column(name = "DOCUMENT_SHA256", length = 64, nullable = false) private String documentSha256;
    @Lob @Column(name = "DOCUMENT_DATA", nullable = false) private byte[] documentData;

    protected AccountStatement() {}
    public AccountStatement(String id, String cifId, String accountReference, String accountType,
                            String maskedAccountReference, LocalDate periodStart, LocalDate periodEnd,
                            String currency, BigDecimal openingBalance, BigDecimal closingBalance,
                            OffsetDateTime generatedAt, String documentSha256, byte[] documentData) {
        this.id = id; this.cifId = cifId; this.accountReference = accountReference; this.accountType = accountType;
        this.maskedAccountReference = maskedAccountReference; this.periodStart = periodStart; this.periodEnd = periodEnd;
        this.currency = currency; this.openingBalance = openingBalance; this.closingBalance = closingBalance;
        this.generatedAt = generatedAt; this.status = "GENERATED"; this.documentSha256 = documentSha256;
        this.documentData = documentData;
    }
    public String getId() { return id; } public String getCifId() { return cifId; }
    public String getAccountReference() { return accountReference; } public String getAccountType() { return accountType; }
    public String getMaskedAccountReference() { return maskedAccountReference; }
    public LocalDate getPeriodStart() { return periodStart; } public LocalDate getPeriodEnd() { return periodEnd; }
    public String getCurrency() { return currency; } public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; } public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public String getStatus() { return status; } public String getDocumentSha256() { return documentSha256; }
    public byte[] getDocumentData() { return documentData; }
}
