package com.moneybags.deposit.dto;

import com.moneybags.deposit.domain.DomainTypes.PaymentOperationType;
import com.moneybags.deposit.domain.DomainTypes.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class PaymentOperationResponses {
    private PaymentOperationResponses() {}

    public record PaymentOperationView(String reservationId, String paymentId,
                                       PaymentOperationType operationType, ReservationStatus status,
                                       String sourceAccountId, String targetAccountId, String externalTargetId,
                                       BigDecimal amount, String currencyCode, OffsetDateTime expiresAt,
                                       List<String> transactionIds) {}
}
