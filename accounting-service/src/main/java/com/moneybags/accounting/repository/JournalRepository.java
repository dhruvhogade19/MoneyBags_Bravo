package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.Journal;
import com.moneybags.accounting.domain.DomainTypes.JournalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, String> {
    @EntityGraph(attributePaths = "lines")
    Optional<Journal> findByJournalNumber(String journalNumber);

    @EntityGraph(attributePaths = "lines")
    Optional<Journal> findByExternalReference(String externalReference);

    @Query("select j from Journal j where (:journalNumber is null or j.journalNumber=:journalNumber) " +
            "and (:businessDate is null or j.businessDate=:businessDate) " +
            "and (:sourceService is null or j.sourceService=:sourceService) " +
            "and (:eventType is null or j.eventType=:eventType) " +
            "and (:externalReference is null or j.externalReference=:externalReference) " +
            "and (:status is null or j.status=:status)")
    Page<Journal> search(@Param("journalNumber") String journalNumber,
                         @Param("businessDate") LocalDate businessDate,
                         @Param("sourceService") String sourceService,
                         @Param("eventType") String eventType,
                         @Param("externalReference") String externalReference,
                         @Param("status") JournalStatus status,
                         Pageable pageable);

    long countByBusinessDate(LocalDate businessDate);

    @Query("select coalesce(sum(j.totalDebit),0), coalesce(sum(j.totalCredit),0) from Journal j " +
            "where j.businessDate=:businessDate")
    List<Object[]> totalsForDate(@Param("businessDate") LocalDate businessDate);

    @Query("select count(j) from Journal j where j.businessDate=:businessDate and j.totalDebit<>j.totalCredit")
    long countUnbalanced(@Param("businessDate") LocalDate businessDate);

    long countByBusinessDateAndCurrencyCodeAndSourceService(LocalDate businessDate, String currencyCode,
                                                             String sourceService);

    long countByBusinessDateAndCurrencyCodeAndSourceServiceAndCorrelationId(LocalDate businessDate,
            String currencyCode, String sourceService, String correlationId);

    @Query("select coalesce(sum(j.totalDebit),0) from Journal j where j.businessDate=:businessDate " +
            "and j.currencyCode=:currency and j.sourceService=:sourceService")
    BigDecimal totalDebit(@Param("businessDate") LocalDate businessDate, @Param("currency") String currency,
                          @Param("sourceService") String sourceService);

    @Query("select coalesce(sum(j.totalDebit),0) from Journal j where j.businessDate=:businessDate " +
            "and j.currencyCode=:currency and j.sourceService=:sourceService " +
            "and j.correlationId=:correlationId")
    BigDecimal totalDebitByCorrelationId(@Param("businessDate") LocalDate businessDate,
            @Param("currency") String currency, @Param("sourceService") String sourceService,
            @Param("correlationId") String correlationId);

    @Query("select coalesce(sum(j.totalDebit),0) from Journal j where j.reversesJournalNumber=:journalNumber")
    BigDecimal totalReversed(@Param("journalNumber") String journalNumber);

    Optional<Journal> findTopByOrderByPostingSequenceDesc();

    @Query(value = "SELECT ACCT_JOURNAL_SEQ.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextPostingSequence();
}
