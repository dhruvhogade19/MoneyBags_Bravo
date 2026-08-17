package com.moneybags.kycservice.repository;

import com.moneybags.kycservice.entity.Kyc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.enums.NotificationSyncStatus;
import com.moneybags.kycservice.enums.CifSyncStatus;

public interface KycRepository
        extends JpaRepository<Kyc, Long> {

    List<Kyc> findAllByCifIdOrderByCreatedAtDesc(
            Long cifId
    );

    java.util.Optional<Kyc> findFirstByCifIdOrderByCreatedAtDesc(Long cifId);

    Page<Kyc> findAllByTenantIdAndKycStatusIn(
            String tenantId, List<KycStatus> statuses, Pageable pageable);

    Page<Kyc> findAllByTenantIdAndCifIdAndKycStatusIn(
            String tenantId, Long cifId, List<KycStatus> statuses, Pageable pageable);

    List<Kyc> findTop50ByNotificationSyncStatusInAndNotificationRetryCountLessThanOrderByUpdatedAtAsc(
            List<NotificationSyncStatus> statuses, Integer retryCount);

    List<Kyc> findTop50ByCifSyncStatusInAndSyncRetryCountLessThanOrderByUpdatedAtAsc(
            List<CifSyncStatus> statuses, Integer retryCount);

}
