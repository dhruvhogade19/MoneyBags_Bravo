package com.moneybags.cif.dto.response;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.domain.enums.KycStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CifResponse(
        Long cifId,
        String firstName,
        String lastName,
        LocalDate dob,
        Integer age,
        String email,
        String number,
        String address,
        EmploymentType employmentType,
        BigDecimal salary,
        KycStatus kycStatus,
        String panNumber,
        String aadhaarNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}