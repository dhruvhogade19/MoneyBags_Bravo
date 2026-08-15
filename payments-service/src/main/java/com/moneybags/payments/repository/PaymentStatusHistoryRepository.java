package com.moneybags.payments.repository;

import com.moneybags.payments.domain.PaymentStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
  List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtAsc(String paymentId);
}
