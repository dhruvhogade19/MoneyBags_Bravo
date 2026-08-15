package com.moneybags.cif.dto.request;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.domain.enums.KycStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KycVerificationRequest(

        Long cifId,
        String firstName,
        String lastName,
        LocalDate dob,
        String email,
        String number,
        String address,
        EmploymentType employmentType,
        BigDecimal salary,
        KycStatus kycStatus,
        String panNumber,
        String aadhaarNumber
) {
}


//  POST/api/v1/kyc-verifications