package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByScopeAndKeyHash(String scope, String keyHash);
}
