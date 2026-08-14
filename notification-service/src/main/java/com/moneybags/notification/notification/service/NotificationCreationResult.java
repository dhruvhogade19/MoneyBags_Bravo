package com.moneybags.notification.notification.service;

import com.moneybags.notification.notification.dto.NotificationResponse;

public record NotificationCreationResult(NotificationResponse notification, boolean replayed) {
}
