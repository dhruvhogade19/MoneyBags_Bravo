package com.moneybags.kycservice.dto.request;

import com.moneybags.kycservice.enums.VerificationStatus;
import jakarta.validation.constraints.NotNull;

public record DocumentVerificationRequest(

        @NotNull
        VerificationStatus status,

        String remarks

) {
}
