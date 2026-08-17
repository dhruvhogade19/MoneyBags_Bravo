package com.moneybags.payments.integration.demo;

import com.moneybags.payments.dto.IntegrationDtos.NotificationRequest;
import com.moneybags.payments.dto.IntegrationDtos.NotificationResponse;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.integration.NotificationClient;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"demo", "test"})
public class DemoNotificationClient implements NotificationClient {
  private final AtomicLong sequence = new AtomicLong(50000);
  private final Map<String, NotificationResponse> sent = new ConcurrentHashMap<>();

  @Override
  public NotificationResponse send(NotificationRequest request, String idempotencyKey,
                                   String correlationId) {
    if (Long.valueOf(999L).equals(request.cifId())) {
      throw new PeerServiceException("NOTIFICATION-SERVICE", 503,
          "NOTIFICATION_UNAVAILABLE", "Demo notification service is unavailable");
    }
    return sent.computeIfAbsent(idempotencyKey, ignored -> {
      Instant now = Instant.now();
      return new NotificationResponse(String.valueOf(sequence.incrementAndGet()),
          request.cifId(), request.notificationType(), request.sourceReference(),
          "SENT", now, now, false);
    });
  }
}
