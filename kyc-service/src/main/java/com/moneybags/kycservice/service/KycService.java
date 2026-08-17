package com.moneybags.kycservice.service;

import com.moneybags.kycservice.integration.cif.CifClient;
import com.moneybags.kycservice.integration.notification.NotificationClient;
import com.moneybags.kycservice.dto.request.CreateKycRequest;
import com.moneybags.kycservice.dto.request.KycDecisionRequest;
import com.moneybags.kycservice.dto.response.KycResponse;
import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.enums.CifSyncStatus;
import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycDecision;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.exception.BadRequestException;
import com.moneybags.kycservice.exception.ResourceNotFoundException;
import com.moneybags.kycservice.mapper.KycMapper;
import com.moneybags.kycservice.repository.KycRepository;
import com.moneybags.kycservice.repository.KycDocumentRepository;
import com.moneybags.kycservice.enums.DocumentType;
import com.moneybags.kycservice.enums.VerificationStatus;
import com.moneybags.kycservice.enums.NotificationSyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class KycService {

    private final KycRepository kycRepository;
    private final KycMapper kycMapper;
    private final CifClient cifClient;
    private final NotificationClient notificationClient;
    private final KycDocumentRepository documentRepository;
    private final boolean documentsRequiredForDecision;

    public KycService(
            KycRepository kycRepository,
            KycMapper kycMapper,
            CifClient cifClient,
            NotificationClient notificationClient,
            KycDocumentRepository documentRepository,
            @Value("${moneybags.kyc.documents-required-for-decision:false}") boolean documentsRequiredForDecision
    ) {

        this.kycRepository = kycRepository;
        this.kycMapper = kycMapper;
        this.cifClient = cifClient;
        this.notificationClient = notificationClient;
        this.documentRepository = documentRepository;
        this.documentsRequiredForDecision = documentsRequiredForDecision;
    }

    @Transactional
    public KycResponse createKyc(
            CreateKycRequest request
    ) {

        validateEmploymentSnapshot(request);

        var existing = kycRepository.findFirstByCifIdOrderByCreatedAtDesc(request.cifId());
        if (existing.isPresent()) {
            Kyc latest = existing.get();
            if (!latest.getTenantId().equals(request.tenantId())) {
                throw new BadRequestException("CIF is already associated with a KYC case in another tenant");
            }
            if (latest.getKycStatus() == KycStatus.PENDING
                    || latest.getKycStatus() == KycStatus.FLAGGED) {
                refreshPendingCase(latest, request);
                return kycMapper.toResponse(kycRepository.save(latest));
            }
        }

        Kyc kyc = kycMapper.toEntity(request);

        Kyc savedKyc = kycRepository.save(kyc);

        return kycMapper.toResponse(savedKyc);
    }

    private void refreshPendingCase(Kyc kyc, CreateKycRequest request) {
        kycMapper.applyCustomerSnapshot(kyc, request);
        kyc.setKycStatus(KycStatus.PENDING);
        kyc.setDecision(null);
        kyc.setRejectionReason(null);
        kyc.setMismatchReason(null);
        kyc.setReviewedBy(null);
        kyc.setReviewedAt(null);
        kyc.setCifSyncStatus(CifSyncStatus.PENDING);
        kyc.setSyncRetryCount(0);
        kyc.setLastSyncAttemptAt(null);
        kyc.setLastSyncError(null);
        kyc.setCifSyncedAt(null);
        kyc.setNotificationSyncStatus(NotificationSyncStatus.NOT_REQUIRED);
        kyc.setNotificationRetryCount(0);
        kyc.setLastNotificationAttemptAt(null);
        kyc.setLastNotificationError(null);
        kyc.setNotificationSentAt(null);
    }

    private void validateEmploymentSnapshot(CreateKycRequest request) {

        if (request.employmentType() == EmploymentType.STUDENT
                && request.salary() != null) {
            throw new BadRequestException(
                    "salary must be empty when employmentType is STUDENT"
            );
        }

        if (request.employmentType() != EmploymentType.STUDENT
                && (request.salary() == null
                || request.salary().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BadRequestException(
                    "salary must be greater than zero for BUSINESS or SALARIED employment"
            );
        }
    }

    @Transactional(readOnly = true)
    public KycResponse getKycById(Long kycId) {

        Kyc kyc = findKyc(kycId);

        return kycMapper.toResponse(kyc);
    }

    @Transactional(readOnly = true)
    public List<KycResponse> getKycsByCifId(
            Long cifId
    ) {

        return kycRepository
                .findAllByCifIdOrderByCreatedAtDesc(cifId)
                .stream()
                .map(kycMapper::toResponse)
                .toList();
    }

    @Transactional
    public KycResponse makeDecision(
            Long kycId,
            KycDecisionRequest request,
            String reviewerId
    ) {

        Kyc kyc = findKyc(kycId);

        validateDecision(kyc, request);

        KycStatus finalStatus =
                request.decision() == KycDecision.APPROVED
                        ? KycStatus.APPROVED
                        : KycStatus.REJECTED;


        OffsetDateTime now = OffsetDateTime.now();

        kyc.setDecision(request.decision());
        kyc.setKycStatus(finalStatus);
        kyc.setReviewedBy(reviewerId);
        kyc.setReviewedAt(now);
        kyc.setUpdatedAt(now);

        if (request.decision() == KycDecision.REJECTED) {
            kyc.setRejectionReason(
                    request.rejectionReason()
            );
        } else {
            kyc.setRejectionReason(null);
        }

        kyc.setCifSyncStatus(CifSyncStatus.PENDING);
        kyc.setNotificationSyncStatus(NotificationSyncStatus.PENDING);
        kyc.setNotificationRetryCount(0);
        kyc.setLastNotificationError(null);

        Kyc savedKyc = kycRepository.save(kyc);

        synchronizeWithCif(savedKyc);

        sendNotification(savedKyc);

        return kycMapper.toResponse(savedKyc);
    }

    @Transactional
    public KycResponse retryCifSync(
            Long kycId
    ) {

        Kyc kyc = findKyc(kycId);

        if (kyc.getKycStatus()
                != KycStatus.APPROVED
                &&
                kyc.getKycStatus()
                        != KycStatus.REJECTED) {

            throw new BadRequestException(
                    "CIF sync can only be retried after a final KYC decision"
            );
        }

        if (kyc.getCifSyncStatus()
                == CifSyncStatus.SYNCED) {

            throw new BadRequestException(
                    "KYC is already synchronized with CIF"
            );
        }

        if (kyc.getSyncRetryCount() >= 5) {

            throw new BadRequestException(
                    "Maximum CIF sync retry limit reached"
            );
        }

        synchronizeWithCif(kyc);

        return kycMapper.toResponse(kyc);
    }

    private void validateDecision(
            Kyc kyc,
            KycDecisionRequest request
    ) {

        if (kyc.getKycStatus() == KycStatus.APPROVED
                || kyc.getKycStatus() == KycStatus.REJECTED) {

            throw new BadRequestException(
                    "KYC decision has already been completed"
            );
        }

        if (request.decision() == KycDecision.REJECTED
                && (
                request.rejectionReason() == null
                        || request.rejectionReason().isBlank()
        )) {

            throw new BadRequestException(
                    "rejectionReason is required when decision is REJECTED"
            );
        }

        if (documentsRequiredForDecision) {
            List<com.moneybags.kycservice.entity.KycDocument> documents =
                    documentRepository.findAllByKycKycId(kyc.getKycId());
            java.util.Set<DocumentType> present = documents.stream()
                    .map(com.moneybags.kycservice.entity.KycDocument::getDocumentType)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.Set<DocumentType> missing = java.util.EnumSet.allOf(DocumentType.class);
            missing.removeAll(present);
            if (!missing.isEmpty()) {
                throw new BadRequestException("All required KYC documents must be uploaded before a decision. Missing: "
                        + missing);
            }
            if (documents.stream().anyMatch(document -> document.getVerificationStatus() == VerificationStatus.PENDING)) {
                throw new BadRequestException("Every KYC document must be VERIFIED or MISMATCH before a decision");
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<KycResponse> getAdminWorkQueue(String tenantId, Long cifId, List<KycStatus> statuses,
                                               Pageable pageable) {
        List<KycStatus> effectiveStatuses = statuses == null || statuses.isEmpty()
                ? List.of(KycStatus.PENDING, KycStatus.FLAGGED) : statuses;
        Page<Kyc> page = cifId == null
                ? kycRepository.findAllByTenantIdAndKycStatusIn(tenantId, effectiveStatuses, pageable)
                : kycRepository.findAllByTenantIdAndCifIdAndKycStatusIn(
                        tenantId, cifId, effectiveStatuses, pageable);
        return page.map(kycMapper::toResponse);
    }

    private void synchronizeWithCif(Kyc kyc) {

        OffsetDateTime now =
                OffsetDateTime.now();

        kyc.setLastSyncAttemptAt(now);
        kyc.setUpdatedAt(now);

        try {

            cifClient.updateKycStatus(
                    kyc.getCifId(),
                    kyc.getKycStatus()
            );

            kyc.setCifSyncStatus(
                    CifSyncStatus.SYNCED
            );

            kyc.setCifSyncedAt(
                    OffsetDateTime.now()
            );

            kyc.setLastSyncError(null);

        } catch (Exception exception) {

            kyc.setCifSyncStatus(
                    CifSyncStatus.FAILED
            );

            kyc.setSyncRetryCount(
                    kyc.getSyncRetryCount() + 1
            );

            kyc.setLastSyncError(
                    exception.getMessage()
            );
        }

        kyc.setUpdatedAt(
                OffsetDateTime.now()
        );

        kycRepository.save(kyc);
    }

    private void sendNotification(Kyc kyc) {
        if (kyc.getKycStatus() != KycStatus.APPROVED && kyc.getKycStatus() != KycStatus.REJECTED) return;
        if (kyc.getNotificationRetryCount() >= 5) return;
        kyc.setLastNotificationAttemptAt(OffsetDateTime.now());
        kyc.setNotificationRetryCount(kyc.getNotificationRetryCount() + 1);
        try {
            var response = notificationClient
                    .sendKycStatusNotification(
                            kyc.getCifId(),
                            kyc.getKycStatus(),
                            kyc.getRejectionReason()
                    );
            if (response != null && "SENT".equals(response.status())) {
                kyc.setNotificationSyncStatus(NotificationSyncStatus.SENT);
                kyc.setNotificationSentAt(OffsetDateTime.now());
                kyc.setLastNotificationError(null);
            } else {
                kyc.setNotificationSyncStatus(NotificationSyncStatus.FAILED);
                kyc.setLastNotificationError("Notification service did not confirm email delivery");
            }
        } catch (Exception exception) {
            kyc.setNotificationSyncStatus(NotificationSyncStatus.FAILED);
            kyc.setLastNotificationError(exception.getMessage());
        }
        kyc.setUpdatedAt(OffsetDateTime.now());
        kycRepository.save(kyc);
    }

    @Scheduled(initialDelayString = "${moneybags.kyc.notification-retry-initial-delay:60000}",
            fixedDelayString = "${moneybags.kyc.notification-retry-delay:60000}")
    @Transactional
    public void retryPendingNotifications() {
        kycRepository.findTop50ByNotificationSyncStatusInAndNotificationRetryCountLessThanOrderByUpdatedAtAsc(
                        List.of(NotificationSyncStatus.PENDING, NotificationSyncStatus.FAILED), 5)
                .forEach(this::sendNotification);
    }

    @Scheduled(initialDelayString = "${moneybags.kyc.cif-retry-initial-delay:60000}",
            fixedDelayString = "${moneybags.kyc.cif-retry-delay:60000}")
    @Transactional
    public void retryPendingCifSynchronizations() {
        kycRepository.findTop50ByCifSyncStatusInAndSyncRetryCountLessThanOrderByUpdatedAtAsc(
                        List.of(CifSyncStatus.PENDING, CifSyncStatus.FAILED), 5)
                .stream()
                .filter(kyc -> kyc.getKycStatus() == KycStatus.APPROVED
                        || kyc.getKycStatus() == KycStatus.REJECTED)
                .forEach(this::synchronizeWithCif);
    }
    @Transactional(readOnly = true)
    Kyc findKyc(Long kycId) {

        return kycRepository
                .findById(kycId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "KYC not found with id: "
                                        + kycId
                        )
                );
    }
}
