package com.moneybags.creditcard.repository;

import com.moneybags.creditcard.entity.CreditCardHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditCardHoldRepository extends JpaRepository<CreditCardHold, Long> {
    Optional<CreditCardHold> findByReferenceId(String referenceId);
}
