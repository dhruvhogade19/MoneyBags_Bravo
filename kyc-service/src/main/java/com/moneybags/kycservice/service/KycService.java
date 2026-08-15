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

    public KycService(
            KycRepository kycRepository,
            KycMapper kycMapper,
            CifClient cifClient,
            NotificationClient notificationClient
    ) {

        this.kycRepository = kycRepository;
        this.kycMapper = kycMapper;
        this.cifClient = cifClient;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public KycResponse createKyc(
            CreateKycRequest request
    ) {

        validateEmploymentSnapshot(request);

        Kyc kyc = kycMapper.toEntity(request);

        Kyc savedKyc = kycRepository.save(kyc);

        return kycMapper.toResponse(savedKyc);
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
            KycDecisionRequest request
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
        kyc.setReviewedBy(request.reviewedBy());
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

        try {

            notificationClient
                    .sendKycStatusNotification(
                            kyc.getCifId(),
                            kyc.getKycStatus(),
                            kyc.getRejectionReason()
                    );

        } catch (Exception exception) {

            /*
             * Notification failure should not undo
             * the KYC decision.
             *
             * Later, this can be replaced by:
             * - retry mechanism
             * - Kafka
             * - outbox pattern
             */
        }
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
