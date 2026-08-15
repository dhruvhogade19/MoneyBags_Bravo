package com.moneybags.notification.notification.service;

import com.moneybags.notification.notification.dto.NotificationResponse;
import com.moneybags.notification.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getCifId(), notification.getNotificationType(),
                notification.getSourceReference(), notification.getEmailSubject(), notification.getEmailBody(),
                notification.getStatus(), notification.getCreatedAt(), notification.getSentAt());
    }
}
