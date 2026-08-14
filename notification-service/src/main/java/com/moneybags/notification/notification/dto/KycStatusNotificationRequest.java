package com.moneybags.notification.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record KycStatusNotificationRequest(
        @NotNull @Positive Long cifId,
        @NotNull KycStatus kycStatus,
        @Size(max = 1_000) String rejectionReason) {
}
