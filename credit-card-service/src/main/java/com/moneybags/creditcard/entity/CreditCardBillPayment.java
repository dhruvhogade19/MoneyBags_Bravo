package com.moneybags.creditcard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Idempotency record for a settled card-bill repayment from Payments. */
@Entity
@Table(name = "CREDIT_CARD_BILL_PAYMENT")
public class CreditCardBillPayment {
    @Id @Column(name = "PAYMENT_ID", nullable = false, length = 64)
    public String paymentId;
    @Column(name = "ACCOUNT_ID", nullable = false)
    public Long accountId;
    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    public BigDecimal amount;
    @Column(name = "APPLIED_AT", nullable = false)
    public OffsetDateTime appliedAt;

    protected CreditCardBillPayment() { }
    public CreditCardBillPayment(String paymentId, Long accountId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.accountId = accountId;
        this.amount = amount;
        this.appliedAt = OffsetDateTime.now();
    }
}
