package com.moneybags.payments.repository;

import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, String> {
  Optional<Payment> findByRequestorCifIdAndIdempotencyKey(Long cifId, String idempotencyKey);

  Page<Payment> findByRequestorCifIdOrderByCreatedAtDesc(Long cifId, Pageable pageable);

  Page<Payment> findByStatusAndBusinessDateOrderByCreatedAtAsc(
      PaymentStatus status, LocalDate businessDate, Pageable pageable);

  Page<Payment> findByBusinessDateOrderByCreatedAtAsc(LocalDate businessDate, Pageable pageable);

  long countByStatusIn(List<PaymentStatus> statuses);

  @Query("select p from Payment p where (p.sourceAccountId = :accountId or "
      + "p.destinationAccountId = :accountId) and p.createdAt >= :from and p.createdAt < :to "
      + "order by p.createdAt asc")
  List<Payment> findStatementPayments(@Param("accountId") String accountId,
      @Param("from") Instant from, @Param("to") Instant to);
}
