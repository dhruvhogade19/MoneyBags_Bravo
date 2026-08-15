package com.moneybags.deposit.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class NotificationClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NotificationClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                              @Value("${moneybags.clients.notification.base-url:http://notification-service}") String baseUrl) {
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public void create(String idempotencyKey, String cifId, String notificationType, String sourceReference,
                       String templateVariablesJson) {
        try {
            Map<String, Object> variables = objectMapper.readValue(templateVariablesJson, new TypeReference<>() {});
            ResponseEntity<Void> response = restClient.post().uri("/internal/v1/notifications")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("cifId", cifId, "notificationType", notificationType,
                            "sourceReference", sourceReference, "templateVariables", variables))
                    .retrieve().toBodilessEntity();
            if (response.getStatusCode().value() != 200) {
                throw new IllegalStateException("Notification service returned HTTP " + response.getStatusCode().value());
            }
        } catch (Exception exception) {
            throw new NotificationDeliveryException(exception);
        }
    }

    public static class NotificationDeliveryException extends RuntimeException {
        NotificationDeliveryException(Throwable cause) { super(cause); }
    }
}
