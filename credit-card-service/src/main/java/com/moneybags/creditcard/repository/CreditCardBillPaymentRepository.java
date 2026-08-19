package com.moneybags.creditcard.repository;

import com.moneybags.creditcard.entity.CreditCardBillPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardBillPaymentRepository extends JpaRepository<CreditCardBillPayment, String> { }
