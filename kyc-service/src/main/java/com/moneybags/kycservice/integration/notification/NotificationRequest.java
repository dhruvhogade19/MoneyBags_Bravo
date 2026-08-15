package com.moneybags.kycservice.integration.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moneybags.kycservice.enums.KycStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationRequest(
        Long cifId,
        KycStatus kycStatus,
        String rejectionReason
) {
}
