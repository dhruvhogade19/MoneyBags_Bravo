package com.moneybags.creditcard.entity;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CREDIT_CARD_ACCOUNT")
public class CreditCardAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACCOUNT_ID")
    public Long id;
    @Column(name = "APPLICATION_ID", nullable = false, unique = true)
    public Long applicationId;
    @Column(name = "CIF_ID", nullable = false)
    public Long cifId;
    @Column(name = "PRODUCT_CODE", nullable = false)
    public String productCode;
    @Column(name = "AGE")
    public Integer age;
    @Column(name = "SALARY", precision = 19, scale = 2)
    public BigDecimal salary;
    @Column(name = "CARD_NUMBER", nullable = false, unique = true)
    public String cardNumber;
    @Column(name = "SANCTIONED_LIMIT", nullable = false, precision = 19, scale = 2)
    public BigDecimal sanctionedLimit;
    @Column(name = "PURCHASE_INTEREST_RATE_SNAPSHOT", precision = 9, scale = 4)
    public BigDecimal purchaseInterestRateSnapshot;
    @Column(name = "AVAILABLE_LIMIT", nullable = false, precision = 19, scale = 2)
    public BigDecimal availableLimit;
    @Column(name = "OUTSTANDING_AMOUNT", nullable = false, precision = 19, scale = 2)
    public BigDecimal outstandingAmount;
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    public AccountStatus status;
    @Column(name = "OPENED_AT", nullable = false)
    public OffsetDateTime openedAt;
}
