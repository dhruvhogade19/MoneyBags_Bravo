package com.moneybags.kycservice.dto.request;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateKycRequestContractTests {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    void deserializesTheUnchangedCifPayload() throws Exception {
        String cifPayload = """
                {
                  "cifId": 42,
                  "firstName": "Asha",
                  "lastName": "Sharma",
                  "dob": "1995-04-12",
                  "email": "asha@example.com",
                  "number": "9876543210",
                  "address": "12 Market Road",
                  "employmentType": "BUSINESS",
                  "salary": 125000.00,
                  "kycStatus": "PENDING",
                  "panNumber": "ABCDE1234F",
                  "aadhaarNumber": "123456789012"
                }
                """;

        CreateKycRequest request = jsonMapper.readValue(
                cifPayload,
                CreateKycRequest.class
        );

        assertAll(
                () -> assertEquals(42L, request.cifId()),
                () -> assertEquals("Asha", request.firstName()),
                () -> assertEquals("Sharma", request.lastName()),
                () -> assertEquals(LocalDate.of(1995, 4, 12), request.dob()),
                () -> assertEquals(EmploymentType.BUSINESS, request.employmentType()),
                () -> assertEquals(new BigDecimal("125000.00"), request.salary()),
                () -> assertEquals(KycStatus.PENDING, request.kycStatus())
        );
    }
}
