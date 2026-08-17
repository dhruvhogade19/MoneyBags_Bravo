package com.moneybags.cif.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.dto.request.CreateCifRequest;
import com.moneybags.cif.repository.CifRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CifServiceImplTest {
    @Test
    void derivesAgeFromDateOfBirthInsteadOfTrustingTheRequest() {
        var repository = org.mockito.Mockito.mock(CifRepository.class);
        var events = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        var service = new CifServiceImpl(repository, events);
        var dob = LocalDate.now().minusYears(30).minusDays(1);
        var request = new CreateCifRequest(
                "Test", "Customer", dob, null, "test.customer@example.com", "9000000000",
                "Test address", EmploymentType.SALARIED, new BigDecimal("50000"),
                "ABCDE1234F", "123456789012");

        org.mockito.Mockito.when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createCif(request, "identity-user", "moneybags");

        assertThat(response.age()).isEqualTo(30);
        assertThat(response.email()).isEqualTo("test.customer@example.com");
    }
}
