package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.AccountLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountLifecycleEventRepository extends JpaRepository<AccountLifecycleEvent, String> {
    Optional<AccountLifecycleEvent> findByEventReference(String eventReference);
    Optional<AccountLifecycleEvent> findByIdempotencyKeyHash(String idempotencyKeyHash);
}
