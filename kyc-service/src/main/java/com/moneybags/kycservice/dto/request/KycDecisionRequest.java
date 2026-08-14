package com.moneybags.kycservice.dto.request;

import com.moneybags.kycservice.enums.KycDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycDecisionRequest(

        @NotNull
        KycDecision decision,

        String rejectionReason,

        @NotBlank
        @Size(max = 100)
        String reviewedBy

) {
}