package com.moneybags.creditcard.repository;

import com.moneybags.creditcard.domain.CreditCardTypes.ApplicationStatus;
import com.moneybags.creditcard.entity.CreditCardApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditCardApplicationRepository extends JpaRepository<CreditCardApplication, Long> {
    List<CreditCardApplication> findByCifIdOrderBySubmittedAtDesc(Long cifId);

    long countByApplicationStatus(ApplicationStatus status);
}
