package com.moneybags.eod;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface EodBusinessDateRepository extends JpaRepository<EodBusinessDateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from EodBusinessDateEntity value where value.id = :id")
    Optional<EodBusinessDateEntity> findForUpdate(@Param("id") Long id);
}

interface EodRunRepository extends JpaRepository<EodRunEntity, String> {
    Optional<EodRunEntity> findByIdempotencyKey(String idempotencyKey);
}

interface EodExceptionRepository extends JpaRepository<EodExceptionEntity, String> {}
