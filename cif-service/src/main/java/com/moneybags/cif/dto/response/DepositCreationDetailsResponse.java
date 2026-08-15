package com.moneybags.cif.dto.response;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.domain.enums.KycStatus;

import java.time.LocalDate;

public record DepositCreationDetailsResponse(

        Long cifId,
        LocalDate dob,
        EmploymentType employmentType,
        KycStatus kycStatus
) {
}