package com.moneybags.cif.dto.response;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.domain.enums.KycStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositCreationDetailsResponse(

        Long cifId,
        LocalDate dob,
        EmploymentType employmentType,
        BigDecimal monthlyIncome,
        KycStatus kycStatus
) {
}
