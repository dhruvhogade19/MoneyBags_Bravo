package com.moneybags.deposit.dto;

import com.moneybags.deposit.domain.DomainTypes.PaymentOperationType;
import com.moneybags.deposit.domain.DomainTypes.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class PaymentOperationResponses {
    private PaymentOperationResponses() {}

    public record PaymentOperationView(String reservationId, String paymentId,
                                       PaymentOperationType operationType, ReservationStatus status,
                                       String sourceAccountId, String targetAccountId, String externalTargetId,
                                       BigDecimal amount, String currencyCode, OffsetDateTime expiresAt,
                                       List<String> transactionIds) {}

    public record FixedDepositFundingReservationView(
            String reservationId, String paymentId, String operationType, String status,
            String sourceAccountId, String targetAccountId, String fixedDepositId,
            BigDecimal amount, String currencyCode, Instant expiresAt) {}

    public record FixedDepositFundingSettlementView(
            String reservationId, String paymentId, String operationType, String status,
            String fixedDepositId, String fixedDepositStatus, List<String> transactionIds) {}

    public record FixedDepositPayoutConfirmationView(
            String fixedDepositId, String paymentId, String status, String payoutAccountId,
            BigDecimal netPayoutAmount, String currencyCode, Instant completedAt) {}
}
