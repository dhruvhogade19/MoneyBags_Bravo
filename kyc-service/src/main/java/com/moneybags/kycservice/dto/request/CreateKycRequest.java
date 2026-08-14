package com.moneybags.kycservice.dto.request;

import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.math.BigDecimal;

public record CreateKycRequest(

        @NotNull
        Long cifId,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotNull
        @Past
        LocalDate dob,

        @NotBlank
        @Pattern(regexp = "^[0-9]{10,15}$")
        @Size(max = 20)
        String number,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$")
        @Size(max = 20)
        String panNumber,

        @NotBlank
        @Pattern(regexp = "^[0-9]{12}$")
        @Size(max = 20)
        String aadhaarNumber,

        @NotBlank
        @Size(max = 500)
        String address,

        @NotNull
        EmploymentType employmentType,

        BigDecimal salary,

        KycStatus kycStatus

) {
}
