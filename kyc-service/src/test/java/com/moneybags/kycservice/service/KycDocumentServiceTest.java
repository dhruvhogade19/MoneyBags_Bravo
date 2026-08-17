package com.moneybags.kycservice.service;

import com.moneybags.kycservice.dto.request.DocumentVerificationRequest;
import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.entity.KycDocument;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.enums.VerificationStatus;
import com.moneybags.kycservice.mapper.KycMapper;
import com.moneybags.kycservice.repository.KycDocumentRepository;
import com.moneybags.kycservice.repository.KycRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycDocumentServiceTest {

    @Test
    void mismatchFlagsTheCaseAndUsesTheAuthenticatedReviewer() {
        KycDocumentRepository documents = mock(KycDocumentRepository.class);
        KycRepository kycs = mock(KycRepository.class);
        KycService kycService = mock(KycService.class);
        KycDocumentService service = new KycDocumentService(documents, kycService, mock(KycMapper.class), kycs);
        Kyc kyc = new Kyc();
        kyc.setKycStatus(KycStatus.PENDING);
        KycDocument document = new KycDocument();
        document.setKyc(kyc);
        document.setVerificationStatus(VerificationStatus.PENDING);
        when(documents.findByDocumentIdAndKycKycId(9L, 7L)).thenReturn(Optional.of(document));
        when(documents.save(any(KycDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.verifyDocument(7L, 9L,
                new DocumentVerificationRequest(VerificationStatus.MISMATCH, "PAN name differs"), "admin-user");

        assertThat(document.getVerifiedBy()).isEqualTo("admin-user");
        assertThat(document.getVerificationStatus()).isEqualTo(VerificationStatus.MISMATCH);
        assertThat(kyc.getKycStatus()).isEqualTo(KycStatus.FLAGGED);
        assertThat(kyc.getMismatchReason()).isEqualTo("PAN name differs");
    }
}
