package com.moneybags.creditcard.repository;

import com.moneybags.creditcard.entity.CreditCardBillingCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditCardBillingChargeRepository extends JpaRepository<CreditCardBillingCharge, Long> {
    Optional<CreditCardBillingCharge> findByBillId(String billId);
}
