package com.moneybags.cif.dto.request;

import com.moneybags.cif.domain.enums.EmploymentType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCifRequest(

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dob,

        @NotNull(message = "Age is required")
        @Min(value = 0, message = "Age cannot be negative")
        Integer age,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Mobile number is required")
        @Pattern(
                regexp = "^[0-9]{10,15}$",
                message = "Mobile number must contain 10 to 15 digits"
        )
        String number,

        @NotBlank(message = "Address is required")
        String address,

        @NotNull(message = "Employment type is required")
        EmploymentType employmentType,

        BigDecimal salary,

        @NotBlank(message = "PAN number is required")
        @Pattern(
                regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$",
                message = "PAN number must be valid"
        )
        String panNumber,

        @NotBlank(message = "Aadhaar number is required")
        @Pattern(
                regexp = "^[0-9]{12}$",
                message = "Aadhaar number must contain 12 digits"
        )
        String aadhaarNumber
) {
}