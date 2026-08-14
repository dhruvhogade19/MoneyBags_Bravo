package com.moneybags.kycservice.integration.cif;

import com.moneybags.kycservice.enums.KycStatus;

public record CifStatusUpdateRequest(
        Long cifId,
        KycStatus kycStatus
) {
}