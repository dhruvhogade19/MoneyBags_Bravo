package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.AccountingPeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, String> {
    Optional<AccountingPeriod> findByBusinessDate(LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AccountingPeriod p where p.businessDate=:businessDate")
    Optional<AccountingPeriod> findByBusinessDateForUpdate(@Param("businessDate") LocalDate businessDate);
}
