package com.moneybags.notification.notification.entity;

import com.moneybags.notification.common.persistence.BooleanToNumberConverter;
import com.moneybags.notification.notification.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "notification_template")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_template_id")
    @JdbcTypeCode(Types.NUMERIC)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, unique = true)
    private NotificationType notificationType;

    @Column(name = "email_subject_template", nullable = false)
    private String emailSubjectTemplate;

    @Column(name = "email_body_template", nullable = false)
    @Lob
    private String emailBodyTemplate;

    @Column(name = "active", nullable = false)
    @Convert(converter = BooleanToNumberConverter.class)
    @JdbcTypeCode(Types.NUMERIC)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationTemplate() {
    }

    public Long getId() {
        return id;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getEmailSubjectTemplate() {
        return emailSubjectTemplate;
    }

    public String getEmailBodyTemplate() {
        return emailBodyTemplate;
    }

    public boolean isActive() {
        return active;
    }
}
