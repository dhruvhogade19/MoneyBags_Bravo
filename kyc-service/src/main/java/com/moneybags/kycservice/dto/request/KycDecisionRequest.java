package com.moneybags.kycservice.dto.request;

import com.moneybags.kycservice.enums.KycDecision;
import jakarta.validation.constraints.NotNull;

public record KycDecisionRequest(

        @NotNull
        KycDecision decision,

        String rejectionReason

) {
}
