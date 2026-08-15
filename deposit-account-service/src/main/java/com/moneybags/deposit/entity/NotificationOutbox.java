package com.moneybags.deposit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "NOTIFICATION_OUTBOX")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {
    @Id @Column(name = "OUTBOX_ID", length = 36) private String id;
    @Column(name = "CIF_ID", length = 36, nullable = false) private String cifId;
    @Column(name = "NOTIFICATION_TYPE", length = 40, nullable = false) private String notificationType;
    @Column(name = "SOURCE_REFERENCE", length = 100, nullable = false) private String sourceReference;
    @Column(name = "IDEMPOTENCY_KEY", length = 160, nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "TEMPLATE_VARIABLES_JSON", columnDefinition = "CLOB", nullable = false) private String templateVariablesJson;
    @Column(name = "STATUS", length = 20, nullable = false) private String status;
    @Column(name = "ATTEMPT_COUNT", nullable = false) private int attemptCount;
    @Column(name = "LAST_ERROR", length = 1000) private String lastError;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "SENT_AT") private OffsetDateTime sentAt;

    public NotificationOutbox(String id, String cifId, String notificationType, String sourceReference,
                              String idempotencyKey, String templateVariablesJson) {
        this.id = id; this.cifId = cifId; this.notificationType = notificationType;
        this.sourceReference = sourceReference; this.idempotencyKey = idempotencyKey;
        this.templateVariablesJson = templateVariablesJson; this.status = "PENDING";
        this.createdAt = OffsetDateTime.now();
    }

    public void delivered() { status = "DELIVERED"; sentAt = OffsetDateTime.now(); lastError = null; attemptCount++; }
    public void failed(String error) { status = "PENDING"; attemptCount++; lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000)); }
}
