package com.moneybags.notification.notification.entity;

import com.moneybags.notification.notification.domain.DeliveryAttemptResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_attempt_id")
    @JdbcTypeCode(Types.NUMERIC)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "attempt_no", nullable = false)
    @JdbcTypeCode(Types.NUMERIC)
    private int attemptNo;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private DeliveryAttemptResult result;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "error_details")
    @Lob
    private String errorDetails;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(
            Notification notification,
            String provider,
            DeliveryAttemptResult result,
            String providerReference,
            String errorDetails,
            OffsetDateTime attemptedAt) {
        this.notification = notification;
        this.attemptNo = 1;
        this.provider = provider;
        this.result = result;
        this.providerReference = providerReference;
        this.errorDetails = errorDetails;
        this.attemptedAt = attemptedAt;
    }
}
