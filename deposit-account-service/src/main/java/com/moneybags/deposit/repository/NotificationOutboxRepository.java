package com.moneybags.deposit.repository;

import com.moneybags.deposit.entity.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, String> {
    List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
