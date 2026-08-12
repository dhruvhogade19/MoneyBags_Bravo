package com.moneybags.deposit.repository;

import com.moneybags.deposit.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByScopeAndKeyHash(String scope, String keyHash);
}

