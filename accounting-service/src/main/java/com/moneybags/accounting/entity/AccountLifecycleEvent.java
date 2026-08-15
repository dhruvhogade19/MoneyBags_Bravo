package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.AccountType;
import com.moneybags.accounting.domain.DomainTypes.LifecycleEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_LIFECYCLE_EVENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountLifecycleEvent {
    @Id @Column(name = "LIFECYCLE_EVENT_ID", length = 36) private String id;
    @Column(name = "EVENT_REFERENCE", length = 160, nullable = false, unique = true) private String eventReference;
    @Column(name = "IDEMPOTENCY_KEY_HASH", length = 64, nullable = false, unique = true) private String idempotencyKeyHash;
    @Column(name = "REQUEST_HASH", length = 64, nullable = false) private String requestHash;
    @Enumerated(EnumType.STRING) @Column(name = "EVENT_TYPE", length = 40, nullable = false) private LifecycleEventType eventType;
    @Enumerated(EnumType.STRING) @Column(name = "ACCOUNT_TYPE", length = 30, nullable = false) private AccountType accountType;
    @Column(name = "ACCOUNT_REFERENCE", length = 100, nullable = false) private String accountReference;
    @Column(name = "BUSINESS_DATE", nullable = false) private LocalDate businessDate;
    @Column(name = "OCCURRED_AT", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "REASON_CODE", length = 40) private String reasonCode;
    @Column(name = "SOURCE_SERVICE", length = 80, nullable = false) private String sourceService;
    @Column(name = "CORRELATION_ID", length = 64, nullable = false) private String correlationId;
    @Column(name = "PROCESSED_AT", nullable = false) private OffsetDateTime processedAt;

    public AccountLifecycleEvent(String id, String eventReference, String idempotencyKeyHash, String requestHash,
                                 LifecycleEventType eventType, AccountType accountType, String accountReference,
                                 LocalDate businessDate, OffsetDateTime occurredAt, String reasonCode,
                                 String sourceService, String correlationId) {
        this.id = id; this.eventReference = eventReference; this.idempotencyKeyHash = idempotencyKeyHash;
        this.requestHash = requestHash; this.eventType = eventType; this.accountType = accountType;
        this.accountReference = accountReference; this.businessDate = businessDate; this.occurredAt = occurredAt;
        this.reasonCode = reasonCode; this.sourceService = sourceService; this.correlationId = correlationId;
        this.processedAt = OffsetDateTime.now();
    }
}
