package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.JournalLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, String> {
    @Query("select l from JournalLine l join fetch l.journal j where l.subledgerReference=:reference " +
            "order by j.postedAt, l.lineNumber")
    List<JournalLine> findAllForAccount(@Param("reference") String reference);

    @Query(value = "select l from JournalLine l join l.journal j where l.subledgerReference=:reference " +
            "and (:fromDate is null or j.businessDate>=:fromDate) and (:toDate is null or j.businessDate<=:toDate)",
            countQuery = "select count(l) from JournalLine l join l.journal j where l.subledgerReference=:reference " +
                    "and (:fromDate is null or j.businessDate>=:fromDate) and (:toDate is null or j.businessDate<=:toDate)")
    Page<JournalLine> findPageForAccount(@Param("reference") String reference,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate,
                                         Pageable pageable);

    @Query("select l.glCode, sum(l.debitAmount), sum(l.creditAmount) from JournalLine l join l.journal j " +
            "where j.businessDate<=:businessDate and j.currencyCode=:currency group by l.glCode")
    List<Object[]> trialBalanceTotals(@Param("businessDate") LocalDate businessDate,
                                      @Param("currency") String currency);
}
