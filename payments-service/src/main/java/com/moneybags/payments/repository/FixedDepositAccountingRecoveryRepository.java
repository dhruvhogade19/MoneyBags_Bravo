package com.moneybags.payments.repository;

import com.moneybags.payments.domain.FixedDepositAccountingRecovery;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FixedDepositAccountingRecoveryRepository
    extends JpaRepository<FixedDepositAccountingRecovery, String> {
  Optional<FixedDepositAccountingRecovery> findByPaymentId(String paymentId);
  Optional<FixedDepositAccountingRecovery> findByIdempotencyKeyHash(String idempotencyKeyHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from FixedDepositAccountingRecovery r where r.recoveryId = :recoveryId")
  Optional<FixedDepositAccountingRecovery> findByIdForUpdate(
      @Param("recoveryId") String recoveryId);
}
