package com.moneybags.payments.repository;

import com.moneybags.payments.domain.PaymentEodControl;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentEodControlRepository extends JpaRepository<PaymentEodControl, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from PaymentEodControl c where c.controlId = :controlId")
  Optional<PaymentEodControl> findByIdForUpdate(@Param("controlId") String controlId);
}
