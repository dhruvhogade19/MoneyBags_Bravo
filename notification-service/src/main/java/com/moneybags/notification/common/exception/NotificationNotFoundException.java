package com.moneybags.notification.common.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long notificationId) {
        super("Notification was not found for notificationId: " + notificationId);
    }
}
