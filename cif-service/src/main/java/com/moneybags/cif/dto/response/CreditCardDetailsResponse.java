package com.moneybags.cif.dto.response;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.domain.enums.KycStatus;

import java.math.BigDecimal;

public record CreditCardDetailsResponse(
        Long cifId,
        Integer age,
        EmploymentType employmentType,
        BigDecimal salary,
        KycStatus kycStatus
) {
}