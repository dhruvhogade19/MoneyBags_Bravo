package com.moneybags.kycservice.service;

import com.moneybags.kycservice.dto.request.KycDecisionRequest;
import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.entity.KycDocument;
import com.moneybags.kycservice.enums.DocumentType;
import com.moneybags.kycservice.enums.CifSyncStatus;
import com.moneybags.kycservice.enums.KycDecision;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.enums.NotificationSyncStatus;
import com.moneybags.kycservice.enums.VerificationStatus;
import com.moneybags.kycservice.exception.BadRequestException;
import com.moneybags.kycservice.integration.cif.CifClient;
import com.moneybags.kycservice.integration.notification.NotificationClient;
import com.moneybags.kycservice.integration.notification.NotificationResponse;
import com.moneybags.kycservice.mapper.KycMapper;
import com.moneybags.kycservice.repository.KycDocumentRepository;
import com.moneybags.kycservice.repository.KycRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KycServiceDecisionTest {

    private KycRepository repository;
    private KycDocumentRepository documentRepository;
    private CifClient cifClient;
    private NotificationClient notificationClient;
    private KycService service;
    private Kyc kyc;

    @BeforeEach
    void setUp() {
        repository = mock(KycRepository.class);
        documentRepository = mock(KycDocumentRepository.class);
        cifClient = mock(CifClient.class);
        notificationClient = mock(NotificationClient.class);
        service = new KycService(repository, mock(KycMapper.class), cifClient, notificationClient,
                documentRepository, true);
        kyc = new Kyc();
        kyc.setCifId(42L);
        kyc.setTenantId("tenant-a");
        kyc.setKycStatus(KycStatus.PENDING);
        when(repository.findById(7L)).thenReturn(Optional.of(kyc));
        when(repository.save(any(Kyc.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void finalDecisionRequiresEveryRequiredDocument() {
        when(documentRepository.findAllByKycKycId(any())).thenReturn(verifiedDocuments().subList(0, 3));

        assertThatThrownBy(() -> service.makeDecision(
                7L, new KycDecisionRequest(KycDecision.APPROVED, null), "admin-user"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    void finalDecisionRequiresEveryDocumentToBeReviewed() {
        List<KycDocument> documents = verifiedDocuments();
        documents.getFirst().setVerificationStatus(VerificationStatus.PENDING);
        when(documentRepository.findAllByKycKycId(any())).thenReturn(documents);

        assertThatThrownBy(() -> service.makeDecision(
                7L, new KycDecisionRequest(KycDecision.APPROVED, null), "admin-user"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("VERIFIED or MISMATCH");
    }

    @Test
    void mismatchDoesNotAutomaticallyRejectAndReviewerComesFromJwtActor() {
        List<KycDocument> documents = verifiedDocuments();
        documents.getFirst().setVerificationStatus(VerificationStatus.MISMATCH);
        when(documentRepository.findAllByKycKycId(any())).thenReturn(documents);
        when(notificationClient.sendKycStatusNotification(42L, KycStatus.APPROVED, null))
                .thenReturn(new NotificationResponse(501L, 42L, "SENT"));

        service.makeDecision(7L, new KycDecisionRequest(KycDecision.APPROVED, null), "admin-user");

        assertThat(kyc.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(kyc.getReviewedBy()).isEqualTo("admin-user");
        assertThat(kyc.getNotificationSyncStatus()).isEqualTo(NotificationSyncStatus.SENT);
        verify(cifClient).updateKycStatus(42L, KycStatus.APPROVED);
    }

    @Test
    void demoModeAllowsApprovalWithoutPhysicalDocuments() {
        KycService demoService = new KycService(
                repository,
                mock(KycMapper.class),
                cifClient,
                notificationClient,
                documentRepository,
                false
        );

        demoService.makeDecision(
                7L,
                new KycDecisionRequest(KycDecision.APPROVED, null),
                "admin-user"
        );

        assertThat(kyc.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(kyc.getReviewedBy()).isEqualTo("admin-user");
        verify(cifClient).updateKycStatus(42L, KycStatus.APPROVED);
    }

    @Test
    void scheduledRetryConvergesAFailedFinalStatusToCif() {
        kyc.setKycStatus(KycStatus.APPROVED);
        kyc.setCifSyncStatus(CifSyncStatus.FAILED);
        kyc.setSyncRetryCount(1);
        when(repository.findTop50ByCifSyncStatusInAndSyncRetryCountLessThanOrderByUpdatedAtAsc(
                List.of(CifSyncStatus.PENDING, CifSyncStatus.FAILED), 5)).thenReturn(List.of(kyc));

        service.retryPendingCifSynchronizations();

        assertThat(kyc.getCifSyncStatus()).isEqualTo(CifSyncStatus.SYNCED);
        verify(cifClient).updateKycStatus(42L, KycStatus.APPROVED);
    }

    private static List<KycDocument> verifiedDocuments() {
        return Arrays.stream(DocumentType.values()).map(type -> {
            KycDocument document = new KycDocument();
            document.setDocumentType(type);
            document.setVerificationStatus(VerificationStatus.VERIFIED);
            return document;
        }).toList();
    }
}
