package com.moneybags.deposit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_BALANCE")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBalance {
    @Id
    @Column(name = "ACCOUNT_ID", length = 36)
    private String accountId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ACCOUNT_ID")
    private DepositAccount account;

    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false)
    private String currencyCode;

    @Column(name = "LEDGER_BALANCE", precision = 19, scale = 4, nullable = false)
    private BigDecimal ledgerBalance;

    @Column(name = "AVAILABLE_BALANCE", precision = 19, scale = 4, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "BLOCKED_AMOUNT", precision = 19, scale = 4, nullable = false)
    private BigDecimal blockedAmount;

    @Column(name = "PROJECTION_VERSION", nullable = false)
    private long projectionVersion;

    @Column(name = "AS_OF", nullable = false)
    private OffsetDateTime asOf;

    @Column(name = "SOURCE_EVENT_ID", length = 100, nullable = false, unique = true)
    private String sourceEventId;

    public static AccountBalance initial(String currency, String sourceEventId) {
        AccountBalance value = new AccountBalance();
        value.currencyCode = currency;
        value.ledgerBalance = BigDecimal.ZERO.setScale(4);
        value.availableBalance = BigDecimal.ZERO.setScale(4);
        value.blockedAmount = BigDecimal.ZERO.setScale(4);
        value.projectionVersion = 0;
        value.asOf = OffsetDateTime.now();
        value.sourceEventId = sourceEventId;
        return value;
    }

    public void reserve(BigDecimal amount, String sourceReference) {
        blockedAmount = blockedAmount.add(amount);
        availableBalance = availableBalance.subtract(amount);
        advance(sourceReference);
    }

    public void release(BigDecimal amount, String sourceReference) {
        blockedAmount = blockedAmount.subtract(amount);
        availableBalance = availableBalance.add(amount);
        advance(sourceReference);
    }

    public void captureDebit(BigDecimal amount, String sourceReference) {
        blockedAmount = blockedAmount.subtract(amount);
        ledgerBalance = ledgerBalance.subtract(amount);
        advance(sourceReference);
    }

    public void credit(BigDecimal amount, String sourceReference) {
        ledgerBalance = ledgerBalance.add(amount);
        availableBalance = availableBalance.add(amount);
        advance(sourceReference);
    }

    private void advance(String sourceReference) {
        projectionVersion++;
        asOf = OffsetDateTime.now();
        sourceEventId = sourceReference;
    }
}
