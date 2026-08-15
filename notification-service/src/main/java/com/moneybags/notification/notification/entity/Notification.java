package com.moneybags.notification.notification.entity;

import com.moneybags.notification.notification.domain.NotificationStatus;
import com.moneybags.notification.notification.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    @JdbcTypeCode(Types.NUMERIC)
    private Long id;

    @Column(name = "cif_id", nullable = false)
    @JdbcTypeCode(Types.NUMERIC)
    private Long cifId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "source_reference", nullable = false)
    private String sourceReference;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "email_subject", nullable = false)
    private String emailSubject;

    @Column(name = "email_body", nullable = false)
    @Lob
    private String emailBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    protected Notification() {
    }

    public Notification(
            Long cifId,
            NotificationType notificationType,
            String sourceReference,
            String idempotencyKey,
            String requestFingerprint,
            String recipientEmail,
            String emailSubject,
            String emailBody,
            OffsetDateTime createdAt) {
        this.cifId = cifId;
        this.notificationType = notificationType;
        this.sourceReference = sourceReference;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.recipientEmail = recipientEmail;
        this.emailSubject = emailSubject;
        this.emailBody = emailBody;
        this.status = NotificationStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void markSent(OffsetDateTime sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public Long getCifId() {
        return cifId;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public String getEmailBody() {
        return emailBody;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }
}
