package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.FinancialReconciliationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.moneybags.accounting.domain.DomainTypes.ReconciliationStatus;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialReconciliationRunRepository extends JpaRepository<FinancialReconciliationRun, String> {
    @EntityGraph(attributePaths = "items") Optional<FinancialReconciliationRun> findDetailedById(String id);
    @EntityGraph(attributePaths = "items")
    Optional<FinancialReconciliationRun> findByEodRunIdAndControlDiscriminatorAndCurrencyCodeAndExecutionEpoch(
            String eodRunId, String controlDiscriminator, String currencyCode, int executionEpoch);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FinancialReconciliationRun r where r.eodRunId=:eodRunId " +
            "and r.controlDiscriminator=:controlDiscriminator and r.currencyCode=:currencyCode " +
            "order by r.executionEpoch desc")
    List<FinancialReconciliationRun> findLogicalRunsForUpdate(@Param("eodRunId") String eodRunId,
                                                               @Param("controlDiscriminator")
                                                               String controlDiscriminator,
                                                               @Param("currencyCode") String currencyCode);
    List<FinancialReconciliationRun> findByBusinessDate(LocalDate businessDate);
    Page<FinancialReconciliationRun> findByActiveTrue(Pageable pageable);
    Page<FinancialReconciliationRun> findByBusinessDateAndActiveTrue(LocalDate businessDate, Pageable pageable);
    @EntityGraph(attributePaths = "items")
    List<FinancialReconciliationRun> findByEodRunIdAndBusinessDateAndActiveTrue(
            String eodRunId, LocalDate businessDate);
    List<FinancialReconciliationRun> findByEodRunIdAndActiveTrueOrderByControlDiscriminatorAsc(String eodRunId);
    @Query(value = "select r.eodRunId from FinancialReconciliationRun r where r.active=true " +
            "group by r.eodRunId order by max(r.createdAt) desc",
            countQuery = "select count(distinct r.eodRunId) from FinancialReconciliationRun r where r.active=true")
    Page<String> findActiveEodRunIds(Pageable pageable);
    long countByBusinessDateAndStatusAndActiveTrue(LocalDate businessDate, ReconciliationStatus status);
}
