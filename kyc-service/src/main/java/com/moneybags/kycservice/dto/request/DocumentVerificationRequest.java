package com.moneybags.kycservice.dto.request;

import com.moneybags.kycservice.enums.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentVerificationRequest(

        @NotNull
        VerificationStatus status,

        String remarks,

        @NotBlank
        @Size(max = 100)
        String verifiedBy

) {
}