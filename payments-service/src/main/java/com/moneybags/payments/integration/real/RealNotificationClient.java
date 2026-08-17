package com.moneybags.payments.integration.real;

import com.moneybags.payments.dto.IntegrationDtos.NotificationRequest;
import com.moneybags.payments.dto.IntegrationDtos.NotificationResponse;
import com.moneybags.payments.integration.NotificationClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("oracle")
public class RealNotificationClient implements NotificationClient {
  private final RestClient client;

  public RealNotificationClient(@Qualifier("notificationRestClient") RestClient client) {
    this.client = client;
  }

  @Override
  public NotificationResponse send(NotificationRequest request, String idempotencyKey,
                                   String correlationId) {
    return RealClientSupport.errors(client.post().uri("/internal/v1/notifications")
        .header("Idempotency-Key", idempotencyKey)
        .header("X-Correlation-Id", correlationId).body(request).retrieve(),
        "NOTIFICATION-SERVICE").body(NotificationResponse.class);
  }
}
