package com.moneybags.creditcard.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "moneybags.credit-card.stub-upstream-clients", havingValue = "false")
public class RestClientNotificationGateway implements NotificationGateway {
    private final RestClient notification;

    public RestClientNotificationGateway(RestClient.Builder builder,
                                         @Value("${moneybags.credit-card.notification-base-url}") String notificationUrl) {
        notification = builder.baseUrl(notificationUrl).build();
    }

    @Override
    public void sendAccountCreated(AccountCreatedNotification notificationRequest) {
        notification.post().uri("/internal/v1/notifications")
                .headers(headers -> {
                    headers.set("Idempotency-Key", "credit-card-" + notificationRequest.sourceReference() + "-created");
                    headers.set("X-Correlation-Id", correlationId());
                })
                .body(notificationRequest).retrieve().toBodilessEntity();
    }

    private String correlationId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String current = servletAttributes.getRequest().getHeader("X-Correlation-Id");
            if (current != null && !current.isBlank()) return current;
        }
        return UUID.randomUUID().toString();
    }
}
