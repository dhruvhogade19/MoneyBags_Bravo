package com.moneybags.creditcard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CREDIT_CARD_BILLING_CHARGE")
public class CreditCardBillingCharge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BILLING_CHARGE_ID")
    public Long id;

    @Column(name = "ACCOUNT_ID", nullable = false)
    public Long accountId;

    @Column(name = "BILL_ID", nullable = false, unique = true, length = 36)
    public String billId;

    @Column(name = "JOURNAL_NUMBER", nullable = false, length = 64)
    public String journalNumber;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    public String currency;

    @Column(name = "APPLIED_AT", nullable = false)
    public OffsetDateTime appliedAt;
}
