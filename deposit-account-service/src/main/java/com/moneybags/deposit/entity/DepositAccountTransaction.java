package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.DepositTransactionType;
import com.moneybags.deposit.domain.DomainTypes.PaymentOperationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "DEPOSIT_ACCOUNT_TRANSACTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositAccountTransaction {
    @Id @Column(name = "TRANSACTION_ID", length = 36)
    private String id;
    @Column(name = "ACCOUNT_ID", length = 36, nullable = false)
    private String accountId;
    @Column(name = "PAYMENT_ID", length = 64, nullable = false)
    private String paymentId;
    @Column(name = "RESERVATION_ID", length = 36, nullable = false)
    private String reservationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", length = 20, nullable = false)
    private DepositTransactionType transactionType;
    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATION_TYPE", length = 32, nullable = false)
    private PaymentOperationType operationType;
    @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    private String currencyCode;
    @Column(name = "BALANCE_BEFORE", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceBefore;
    @Column(name = "BALANCE_AFTER", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceAfter;
    @Column(name = "CORRELATION_ID", length = 64, nullable = false)
    private String correlationId;
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public DepositAccountTransaction(String id, String accountId, String paymentId, String reservationId,
                                     DepositTransactionType transactionType, PaymentOperationType operationType,
                                     BigDecimal amount, String currencyCode, BigDecimal balanceBefore,
                                     BigDecimal balanceAfter, String correlationId) {
        this.id = id;
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.transactionType = transactionType;
        this.operationType = operationType;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.correlationId = correlationId;
        this.createdAt = OffsetDateTime.now();
    }
}
