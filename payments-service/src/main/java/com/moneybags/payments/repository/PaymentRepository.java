package com.moneybags.payments.repository;

import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentType;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, String> {
  Optional<Payment> findByRequestorCifIdAndIdempotencyKey(Long cifId, String idempotencyKey);

  Page<Payment> findByRequestorCifIdOrderByCreatedAtDesc(Long cifId, Pageable pageable);

  Page<Payment> findByStatusAndBusinessDateOrderByCreatedAtAsc(
      PaymentStatus status, LocalDate businessDate, Pageable pageable);

  Page<Payment> findByBusinessDateOrderByCreatedAtAsc(LocalDate businessDate, Pageable pageable);

  Page<Payment> findByPaymentTypeAndStatusOrderByCreatedAtAsc(
      PaymentType paymentType, PaymentStatus status, Pageable pageable);

  long countByStatusIn(List<PaymentStatus> statuses);

  long countByStatusInAndBusinessDateAndCurrencyCode(
      List<PaymentStatus> statuses, LocalDate businessDate, String currencyCode);

  @Query("select count(p) from Payment p where p.businessDate = :businessDate "
      + "and p.currencyCode = :currencyCode and p.paymentType in :paymentTypes "
      + "and p.accountingJournalNumber is not null")
  long countPaymentServiceJournals(
      @Param("businessDate") LocalDate businessDate,
      @Param("currencyCode") String currencyCode,
      @Param("paymentTypes") List<PaymentType> paymentTypes);

  @Query("select sum(p.amount) from Payment p where p.businessDate = :businessDate "
      + "and p.currencyCode = :currencyCode and p.paymentType in :paymentTypes "
      + "and p.accountingJournalNumber is not null")
  BigDecimal totalPaymentServiceJournalDebits(
      @Param("businessDate") LocalDate businessDate,
      @Param("currencyCode") String currencyCode,
      @Param("paymentTypes") List<PaymentType> paymentTypes);

  @Query("select count(p) from Payment p where p.reversalBusinessDate = :businessDate "
      + "and p.currencyCode = :currencyCode and p.reversalJournalNumber is not null")
  long countPaymentServiceReversalJournals(
      @Param("businessDate") LocalDate businessDate,
      @Param("currencyCode") String currencyCode);

  @Query("select sum(p.amount) from Payment p where p.reversalBusinessDate = :businessDate "
      + "and p.currencyCode = :currencyCode and p.reversalJournalNumber is not null")
  BigDecimal totalPaymentServiceReversalDebits(
      @Param("businessDate") LocalDate businessDate,
      @Param("currencyCode") String currencyCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.paymentId = :paymentId")
  Optional<Payment> findByIdForUpdate(@Param("paymentId") String paymentId);

  @Query("select p from Payment p where (p.sourceAccountId = :accountId or "
      + "p.destinationAccountId = :accountId) and p.createdAt >= :from and p.createdAt < :to "
      + "order by p.createdAt asc")
  List<Payment> findStatementPayments(@Param("accountId") String accountId,
      @Param("from") Instant from, @Param("to") Instant to);
}
