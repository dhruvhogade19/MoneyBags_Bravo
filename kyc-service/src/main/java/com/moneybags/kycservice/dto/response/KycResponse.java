package com.moneybags.kycservice.dto.response;

import com.moneybags.kycservice.enums.CifSyncStatus;
import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycDecision;
import com.moneybags.kycservice.enums.KycStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record KycResponse(

        Long kycId,

        Long cifId,

        String customerName,

        LocalDate dateOfBirth,

        String mobileNumber,

        String email,

        String panNumber,

        String aadhaarNumber,

        String addressLine1,

        String addressLine2,

        String city,

        String state,

        String postalCode,

        String country,

        EmploymentType employmentType,

        BigDecimal salary,

        KycStatus kycStatus,

        KycDecision decision,

        String rejectionReason,

        String mismatchReason,

        String initiatedBy,

        String reviewedBy,

        OffsetDateTime initiatedAt,

        OffsetDateTime reviewedAt,

        CifSyncStatus cifSyncStatus,

        Integer syncRetryCount,

        OffsetDateTime cifSyncedAt,

        OffsetDateTime createdAt,

        OffsetDateTime updatedAt

) {
}
