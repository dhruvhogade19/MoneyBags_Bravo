package com.moneybags.creditcard.repository;

import com.moneybags.creditcard.entity.CreditCardBillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CreditCardBillPaymentRepository extends JpaRepository<CreditCardBillPayment, String> {
    List<CreditCardBillPayment> findByAccountId(Long accountId);
}
