package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.PaymentOperationType;
import com.moneybags.deposit.domain.DomainTypes.ReservationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "FUND_RESERVATION", uniqueConstraints =
        @UniqueConstraint(name = "UQ_RESERVATION_PAYMENT_OP", columnNames = {"PAYMENT_ID", "OPERATION_TYPE"}))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundReservation {
    @Id @Column(name = "RESERVATION_ID", length = 36)
    private String id;
    @Column(name = "PAYMENT_ID", length = 64, nullable = false)
    private String paymentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATION_TYPE", length = 32, nullable = false)
    private PaymentOperationType operationType;
    @Column(name = "SOURCE_ACCOUNT_ID", length = 36, nullable = false)
    private String sourceAccountId;
    @Column(name = "TARGET_ACCOUNT_ID", length = 36)
    private String targetAccountId;
    @Column(name = "EXTERNAL_TARGET_ID", length = 64)
    private String externalTargetId;
    @Column(name = "REQUESTOR_CUSTOMER_ID", length = 36, nullable = false)
    private String requestorCustomerId;
    @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    private String currencyCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 16, nullable = false)
    private ReservationStatus status;
    @Column(name = "EXPIRES_AT", nullable = false)
    private OffsetDateTime expiresAt;
    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false)
    private long version;

    public FundReservation(String id, String paymentId, PaymentOperationType operationType,
                           String sourceAccountId, String targetAccountId, String externalTargetId,
                           String requestorCustomerId, BigDecimal amount, String currencyCode,
                           OffsetDateTime expiresAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.operationType = operationType;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.externalTargetId = externalTargetId;
        this.requestorCustomerId = requestorCustomerId;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.status = ReservationStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(ReservationStatus target) {
        status = target;
        updatedAt = OffsetDateTime.now();
    }
}
