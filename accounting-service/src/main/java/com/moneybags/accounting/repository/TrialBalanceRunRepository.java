package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.TrialBalanceRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface TrialBalanceRunRepository extends JpaRepository<TrialBalanceRun, String> {
    @EntityGraph(attributePaths = "lines") Optional<TrialBalanceRun> findDetailedById(String id);
    @EntityGraph(attributePaths = "lines") Optional<TrialBalanceRun> findByBusinessDateAndCurrencyCode(
            LocalDate businessDate, String currencyCode);
    List<TrialBalanceRun> findByBusinessDate(LocalDate businessDate);
}
