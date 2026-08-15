package com.moneybags.kycservice.integration.cif;

import com.moneybags.kycservice.enums.KycStatus;

public record CifStatusUpdateResponse(
        Long cifId,
        KycStatus kycStatus
) {
}