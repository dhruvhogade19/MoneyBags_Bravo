package com.moneybags.cif.dto.request;

import com.moneybags.cif.domain.enums.KycStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateKycStatusRequest(

        @NotNull(message = "KYC status is required")
        KycStatus kycStatus
) {
}