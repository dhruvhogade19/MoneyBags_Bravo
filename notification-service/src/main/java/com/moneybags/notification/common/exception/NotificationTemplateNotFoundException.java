package com.moneybags.notification.common.exception;

import com.moneybags.notification.notification.domain.NotificationType;

public class NotificationTemplateNotFoundException extends RuntimeException {

    public NotificationTemplateNotFoundException(NotificationType notificationType) {
        super("No active notification template exists for type: " + notificationType);
    }
}
