package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.Journal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, String> {
    @EntityGraph(attributePaths = "lines")
    Optional<Journal> findByJournalNumber(String journalNumber);

    @EntityGraph(attributePaths = "lines")
    Optional<Journal> findByExternalReference(String externalReference);

    @Query("select j from Journal j where (:businessDate is null or j.businessDate=:businessDate) " +
            "and (:sourceService is null or j.sourceService=:sourceService) " +
            "and (:eventType is null or j.eventType=:eventType) " +
            "and (:externalReference is null or j.externalReference=:externalReference)")
    Page<Journal> search(@Param("businessDate") LocalDate businessDate,
                         @Param("sourceService") String sourceService,
                         @Param("eventType") String eventType,
                         @Param("externalReference") String externalReference,
                         Pageable pageable);

    long countByBusinessDateAndCurrencyCodeAndSourceService(LocalDate businessDate, String currencyCode,
                                                             String sourceService);

    @Query("select coalesce(sum(j.totalDebit),0) from Journal j where j.businessDate=:businessDate " +
            "and j.currencyCode=:currency and j.sourceService=:sourceService")
    BigDecimal totalDebit(@Param("businessDate") LocalDate businessDate, @Param("currency") String currency,
                          @Param("sourceService") String sourceService);

    @Query("select coalesce(sum(j.totalDebit),0) from Journal j where j.reversesJournalNumber=:journalNumber")
    BigDecimal totalReversed(@Param("journalNumber") String journalNumber);

    Optional<Journal> findTopByOrderByPostingSequenceDesc();

    @Query(value = "SELECT ACCOUNTING_JOURNAL_SEQ.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextPostingSequence();
}
