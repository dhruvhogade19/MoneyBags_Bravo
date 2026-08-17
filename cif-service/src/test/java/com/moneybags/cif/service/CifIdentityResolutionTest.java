package com.moneybags.cif.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.domain.enums.KycStatus;
import com.moneybags.cif.entity.Cif;
import com.moneybags.cif.dto.request.CreateCifRequest;
import com.moneybags.cif.repository.CifRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CifIdentityResolutionTest {

    @Autowired private CifRepository repository;
    @Autowired private CifService service;

    @Test
    void resolvesAnExistingCifFromTheAuthenticatedIdentity() {
        Cif cif = new Cif();
        cif.setIdentityUserId("identity-user-42");
        cif.setTenantId("moneybags");
        cif.setFirstName("Existing");
        cif.setLastName("Customer");
        cif.setDob(LocalDate.of(1990, 1, 1));
        cif.setAge(36);
        cif.setEmail("existing.customer@test.com");
        cif.setNumber("9000000042");
        cif.setAddress("Demo address");
        cif.setEmploymentType(EmploymentType.SALARIED);
        cif.setSalary(new BigDecimal("100000"));
        cif.setKycStatus(KycStatus.PENDING);
        cif.setPanNumber("ABCDE0042F");
        cif.setAadhaarNumber("100000000042");
        Cif saved = repository.saveAndFlush(cif);

        var response = service.getCifByIdentityUserId("identity-user-42");

        assertThat(response.cifId()).isEqualTo(saved.getCifId());
        assertThat(response.firstName()).isEqualTo("Existing");
    }

    @Test
    void reclaimsAnOrphanedCifWhenStrongIdentityFieldsMatch() {
        Cif cif = new Cif();
        cif.setIdentityUserId("obsolete-identity-user");
        cif.setTenantId("moneybags");
        cif.setFirstName("Existing");
        cif.setLastName("Customer");
        cif.setDob(LocalDate.of(1990, 1, 1));
        cif.setAge(36);
        cif.setEmail("reclaim.customer@test.com");
        cif.setNumber("9000000043");
        cif.setAddress("Demo address");
        cif.setEmploymentType(EmploymentType.SALARIED);
        cif.setSalary(new BigDecimal("100000"));
        cif.setKycStatus(KycStatus.APPROVED);
        cif.setPanNumber("ABCDE0043F");
        cif.setAadhaarNumber("100000000043");
        Cif saved = repository.saveAndFlush(cif);

        var request = new CreateCifRequest(
                "Existing",
                "Customer",
                LocalDate.of(1990, 1, 1),
                36,
                "reclaim.customer@test.com",
                "9000000043",
                "Demo address",
                EmploymentType.SALARIED,
                new BigDecimal("100000"),
                "ABCDE0043F",
                "100000000043"
        );

        var response = service.createCif(request, "current-identity-user", "moneybags");

        assertThat(response.cifId()).isEqualTo(saved.getCifId());
        assertThat(repository.findByIdentityUserId("current-identity-user")).isPresent();
        assertThat(repository.findByIdentityUserId("obsolete-identity-user")).isEmpty();
        assertThat(response.kycStatus()).isEqualTo(KycStatus.APPROVED);
    }
}
