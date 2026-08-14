package com.moneybags.kycservice.mapper;

import com.moneybags.kycservice.dto.request.CreateKycRequest;
import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KycMapperTests {

    private final KycMapper mapper = new KycMapper();

    @Test
    void mapsEmploymentDetailsIntoSnapshot() {
        CreateKycRequest request = new CreateKycRequest(
                42L,
                "Asha",
                "Sharma",
                LocalDate.of(1995, 4, 12),
                "9876543210",
                "asha@example.com",
                "ABCDE1234F",
                "123456789012",
                "12 Market Road",
                EmploymentType.SALARIED,
                new BigDecimal("75000.00"),
                KycStatus.PENDING
        );

        Kyc snapshot = mapper.toEntity(request);

        assertAll(
                () -> assertEquals(EmploymentType.SALARIED, snapshot.getEmploymentType()),
                () -> assertEquals(new BigDecimal("75000.00"), snapshot.getSalary()),
                () -> assertEquals("Asha Sharma", snapshot.getCustomerName()),
                () -> assertEquals("CIF_SERVICE", snapshot.getInitiatedBy())
        );
    }
}
