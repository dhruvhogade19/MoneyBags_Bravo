package com.moneybags.payments.integration;

import com.moneybags.payments.dto.IntegrationDtos.NotificationRequest;
import com.moneybags.payments.dto.IntegrationDtos.NotificationResponse;

public interface NotificationClient {
  NotificationResponse send(NotificationRequest request, String idempotencyKey,
                            String correlationId);
}
