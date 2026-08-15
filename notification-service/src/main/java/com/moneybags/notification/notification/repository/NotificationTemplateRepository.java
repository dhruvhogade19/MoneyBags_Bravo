package com.moneybags.notification.notification.repository;

import com.moneybags.notification.notification.domain.NotificationType;
import com.moneybags.notification.notification.entity.NotificationTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByNotificationTypeAndActiveTrue(NotificationType notificationType);
}
