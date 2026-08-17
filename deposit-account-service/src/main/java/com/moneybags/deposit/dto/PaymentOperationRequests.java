package com.moneybags.deposit.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class PaymentOperationRequests {
    private PaymentOperationRequests() {}

    public record BookTransferReservationRequest(
            @NotBlank @Size(max = 64) String paymentId,
            @NotBlank @Size(max = 36) String requestorCustomerId,
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotBlank @Size(max = 36) String targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            OffsetDateTime expiresAt) {}

    public record CardRepaymentReservationRequest(
            @NotBlank @Size(max = 64) String paymentId,
            @NotBlank @Size(max = 36) String requestorCustomerId,
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotBlank @Size(max = 64) String creditCardAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            OffsetDateTime expiresAt) {}

    public record SettlementRequest(@NotBlank @Size(max = 36) String reservationId) {}

    public record ReleaseReservationRequest(@NotBlank @Size(max = 64) String paymentId,
                                            @Size(max = 80) String reasonCode) {}
}
