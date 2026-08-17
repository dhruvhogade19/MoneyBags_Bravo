package com.moneybags.kycservice.service;

import com.moneybags.kycservice.dto.request.CreateKycRequest;
import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycDecision;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.integration.cif.CifClient;
import com.moneybags.kycservice.integration.notification.NotificationClient;
import com.moneybags.kycservice.mapper.KycMapper;
import com.moneybags.kycservice.repository.KycDocumentRepository;
import com.moneybags.kycservice.repository.KycRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KycServiceResubmissionTest {

    private KycRepository repository;
    private KycService service;

    @BeforeEach
    void setUp() {
        repository = mock(KycRepository.class);
        when(repository.save(any(Kyc.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new KycService(
                repository,
                new KycMapper(),
                mock(CifClient.class),
                mock(NotificationClient.class),
                mock(KycDocumentRepository.class),
                false
        );
    }

    @Test
    void approvedCaseCreatesANewPendingReviewForCorrectedProfile() {
        Kyc approved = existingCase(KycStatus.APPROVED);
        approved.setDecision(KycDecision.APPROVED);
        when(repository.findFirstByCifIdOrderByCreatedAtDesc(42L)).thenReturn(Optional.of(approved));

        service.createKyc(correctedRequest());

        var captor = org.mockito.ArgumentCaptor.forClass(Kyc.class);
        verify(repository).save(captor.capture());
        Kyc saved = captor.getValue();
        assertThat(saved).isNotSameAs(approved);
        assertThat(saved.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(saved.getDecision()).isNull();
        assertThat(saved.getCustomerName()).isEqualTo("Corrected Customer");
        assertThat(saved.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 6, 8));
        assertThat(saved.getSalary()).isEqualByComparingTo("100000");
    }

    @Test
    void pendingCaseIsRefreshedInsteadOfDuplicated() {
        Kyc pending = existingCase(KycStatus.PENDING);
        pending.setCustomerName("Old Name");
        when(repository.findFirstByCifIdOrderByCreatedAtDesc(42L)).thenReturn(Optional.of(pending));

        service.createKyc(correctedRequest());

        assertThat(pending.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(pending.getCustomerName()).isEqualTo("Corrected Customer");
        assertThat(pending.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 6, 8));
        assertThat(pending.getSalary()).isEqualByComparingTo("100000");
        verify(repository).save(pending);
    }

    private static Kyc existingCase(KycStatus status) {
        Kyc kyc = new Kyc();
        kyc.setCifId(42L);
        kyc.setTenantId("moneybags");
        kyc.setKycStatus(status);
        return kyc;
    }

    private static CreateKycRequest correctedRequest() {
        return new CreateKycRequest(
                42L, "moneybags", "Corrected", "Customer", LocalDate.of(2000, 6, 8),
                "1234567892", "corrected@example.com", "AIRUW1234D", "145676448722",
                "Pune", EmploymentType.SALARIED, new BigDecimal("100000"), KycStatus.PENDING
        );
    }
}
