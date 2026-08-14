package com.moneybags.creditcard.entity;

import com.moneybags.creditcard.domain.CreditCardTypes.HoldStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CREDIT_CARD_HOLD")
public class CreditCardHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HOLD_ID")
    public Long id;

    @Column(name = "ACCOUNT_ID", nullable = false)
    public Long accountId;

    @Column(name = "REFERENCE_ID", nullable = false, unique = true)
    public String referenceId;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    public HoldStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    public OffsetDateTime createdAt;
}
