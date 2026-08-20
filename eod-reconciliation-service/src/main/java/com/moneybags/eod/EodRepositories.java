package com.moneybags.eod;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface EodBusinessDateRepository extends JpaRepository<EodBusinessDateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from EodBusinessDateEntity value where value.id = :id")
    Optional<EodBusinessDateEntity> findForUpdate(@Param("id") Long id);
}

interface EodRunRepository extends JpaRepository<EodRunEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from EodRunEntity value where value.id = :id")
    Optional<EodRunEntity> findForUpdate(@Param("id") String id);
    Optional<EodRunEntity> findByIdempotencyKey(String idempotencyKey);
    List<EodRunEntity> findAllByBusinessDateOrderByStartedAtDesc(LocalDate businessDate);
    List<EodRunEntity> findTop50ByOrderByStartedAtDesc();

    @Query("select value.id from EodRunEntity value where value.status in :statuses order by value.startedAt")
    List<String> findIdsByStatusIn(@Param("statuses") Collection<String> statuses);
}

interface EodExceptionRepository extends JpaRepository<EodExceptionEntity, String> {}

interface EodRunActionRepository extends JpaRepository<EodRunActionEntity, String> {
    List<EodRunActionEntity> findAllByRunIdOrderByCreatedAtAsc(String runId);
    long countByRunIdAndActionType(String runId, String actionType);
    Optional<EodRunActionEntity> findByRunIdAndRequestKindAndRequestKey(
            String runId, String requestKind, String requestKey);
}
