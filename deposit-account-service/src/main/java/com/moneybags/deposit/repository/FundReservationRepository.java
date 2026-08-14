package com.moneybags.deposit.repository;

import com.moneybags.deposit.domain.DomainTypes.PaymentOperationType;
import com.moneybags.deposit.domain.DomainTypes.ReservationStatus;
import com.moneybags.deposit.entity.FundReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FundReservationRepository extends JpaRepository<FundReservation, String> {
    Optional<FundReservation> findByPaymentIdAndOperationType(String paymentId, PaymentOperationType operationType);
    Optional<FundReservation> findByPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FundReservation r where r.id = :id")
    Optional<FundReservation> findLockedById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FundReservation r where r.paymentId = :paymentId and r.operationType = :operationType")
    Optional<FundReservation> findLockedByPaymentIdAndOperationType(@Param("paymentId") String paymentId,
                                                                    @Param("operationType") PaymentOperationType operationType);

    List<FundReservation> findTop100ByStatusAndExpiresAtBefore(ReservationStatus status, OffsetDateTime now);
    long countBySourceAccountIdAndStatus(String accountId, ReservationStatus status);
}
