package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.FinancialReconciliationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.moneybags.accounting.domain.DomainTypes.ReconciliationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialReconciliationRunRepository extends JpaRepository<FinancialReconciliationRun, String> {
    @EntityGraph(attributePaths = "items") Optional<FinancialReconciliationRun> findDetailedById(String id);
    @EntityGraph(attributePaths = "items") Optional<FinancialReconciliationRun> findByEodRunIdAndCurrencyCode(
            String eodRunId, String currencyCode);
    List<FinancialReconciliationRun> findByBusinessDate(LocalDate businessDate);
    Page<FinancialReconciliationRun> findByBusinessDate(LocalDate businessDate, Pageable pageable);
    Optional<FinancialReconciliationRun> findTopByEodRunIdOrderByCreatedAtDesc(String eodRunId);
    long countByBusinessDateAndStatus(LocalDate businessDate, ReconciliationStatus status);
}
