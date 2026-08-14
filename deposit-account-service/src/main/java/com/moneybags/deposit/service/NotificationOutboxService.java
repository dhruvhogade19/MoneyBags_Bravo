package com.moneybags.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.entity.NotificationOutbox;
import com.moneybags.deposit.integration.NotificationClient;
import com.moneybags.deposit.repository.NotificationOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationOutboxService {
    private final NotificationOutboxRepository repository;
    private final NotificationClient client;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final boolean dispatchEnabled;

    public NotificationOutboxService(NotificationOutboxRepository repository, NotificationClient client, ObjectMapper objectMapper,
                                     TransactionTemplate transactionTemplate,
                                     @Value("${moneybags.notifications.dispatch-enabled:true}") boolean dispatchEnabled) {
        this.repository = repository; this.client = client; this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate; this.dispatchEnabled = dispatchEnabled;
    }

    public void enqueue(String cifId, String type, String sourceReference, String idempotencyKey,
                        Map<String, ?> templateVariables) {
        try {
            repository.save(new NotificationOutbox(UUID.randomUUID().toString(), cifId, type, sourceReference,
                    idempotencyKey, objectMapper.writeValueAsString(templateVariables)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize notification template variables", exception);
        }
    }

    @Scheduled(fixedDelayString = "${moneybags.notifications.dispatch-delay:5000}")
    public void dispatchPending() {
        if (!dispatchEnabled) return;
        repository.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, 50))
                .forEach(outbox -> transactionTemplate.executeWithoutResult(status -> dispatch(outbox.getId())));
    }

    private void dispatch(String outboxId) {
        NotificationOutbox outbox = repository.findById(outboxId).orElseThrow();
        if (!"PENDING".equals(outbox.getStatus())) return;
        try {
            client.create(outbox.getIdempotencyKey(), outbox.getCifId(), outbox.getNotificationType(),
                    outbox.getSourceReference(), outbox.getTemplateVariablesJson());
            outbox.delivered();
        } catch (NotificationClient.NotificationDeliveryException exception) {
            outbox.failed(exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage());
        }
    }
}
