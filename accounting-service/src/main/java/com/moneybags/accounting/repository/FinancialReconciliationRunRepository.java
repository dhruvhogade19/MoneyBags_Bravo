package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.FinancialReconciliationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialReconciliationRunRepository extends JpaRepository<FinancialReconciliationRun, String> {
    @EntityGraph(attributePaths = "items") Optional<FinancialReconciliationRun> findDetailedById(String id);
    @EntityGraph(attributePaths = "items") Optional<FinancialReconciliationRun> findByEodRunIdAndCurrencyCode(
            String eodRunId, String currencyCode);
    List<FinancialReconciliationRun> findByBusinessDate(LocalDate businessDate);
}
