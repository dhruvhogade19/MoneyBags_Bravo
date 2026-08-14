package com.moneybags.notification.notification.repository;

import com.moneybags.notification.notification.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
}
