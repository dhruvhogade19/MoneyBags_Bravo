package com.moneybags.accounting.repository;

import com.moneybags.accounting.domain.DomainTypes.ReconciliationItemStatus;
import com.moneybags.accounting.entity.FinancialReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface FinancialReconciliationItemRepository extends JpaRepository<FinancialReconciliationItem, String> {
    long countByBlockingTrueAndStatus(ReconciliationItemStatus status);

    @Query("select count(i) from FinancialReconciliationItem i where i.blocking=true and i.status=:status " +
            "and i.run.businessDate=:businessDate")
    long countBlockingForDate(@Param("businessDate") LocalDate businessDate,
                              @Param("status") ReconciliationItemStatus status);
}
