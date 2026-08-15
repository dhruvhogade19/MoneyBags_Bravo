package com.moneybags.cif.domain.event;

import com.moneybags.cif.dto.request.KycVerificationRequest;

public record CifCreatedEvent(
        KycVerificationRequest kycVerificationRequest
) {
}