package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.TrialBalanceRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface TrialBalanceRunRepository extends JpaRepository<TrialBalanceRun, String> {
    @EntityGraph(attributePaths = "lines") Optional<TrialBalanceRun> findDetailedById(String id);
    @EntityGraph(attributePaths = "lines")
    Optional<TrialBalanceRun> findByBusinessDateAndCurrencyCodeAndExecutionEpoch(
            LocalDate businessDate, String currencyCode, int executionEpoch);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TrialBalanceRun r where r.businessDate=:businessDate " +
            "and r.currencyCode=:currencyCode order by r.executionEpoch desc")
    List<TrialBalanceRun> findLogicalRunsForUpdate(@Param("businessDate") LocalDate businessDate,
                                                   @Param("currencyCode") String currencyCode);
    List<TrialBalanceRun> findByBusinessDate(LocalDate businessDate);
    List<TrialBalanceRun> findByBusinessDateAndActiveTrue(LocalDate businessDate);
    Page<TrialBalanceRun> findByActiveTrue(Pageable pageable);
    Page<TrialBalanceRun> findByBusinessDateAndActiveTrue(LocalDate businessDate, Pageable pageable);
}
