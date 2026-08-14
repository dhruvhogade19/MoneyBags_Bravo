package com.moneybags.payments.repository;

import com.moneybags.payments.domain.PaymentAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
  List<PaymentAttempt> findByPaymentIdOrderByStartedAtAsc(String paymentId);
}
