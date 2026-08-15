package com.moneybags.creditcard.repository;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import com.moneybags.creditcard.entity.CreditCardAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CreditCardAccountRepository extends JpaRepository<CreditCardAccount, Long> {
    List<CreditCardAccount> findByCifIdOrderByOpenedAtDesc(Long cifId);

    long countByStatus(AccountStatus status);

    boolean existsByApplicationId(Long applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditCardAccount a where a.id = :id")
    Optional<CreditCardAccount> lockById(Long id);
}
