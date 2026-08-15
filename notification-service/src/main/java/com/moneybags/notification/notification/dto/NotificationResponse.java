package com.moneybags.notification.notification.dto;

import com.moneybags.notification.notification.domain.NotificationStatus;
import com.moneybags.notification.notification.domain.NotificationType;
import java.time.OffsetDateTime;

public record NotificationResponse(
        Long notificationId,
        Long cifId,
        NotificationType notificationType,
        String sourceReference,
        String emailSubject,
        String emailBody,
        NotificationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt) {
}
