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
@Table(name = "AUDIT_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {
    @Id @Column(name = "AUDIT_ID", length = 36)
    private String id;
    @Column(name = "AGGREGATE_ID", length = 36)
    private String aggregateId;
    @Column(name = "ACTION", length = 80, nullable = false)
    private String action;
    @Column(name = "OUTCOME", length = 20, nullable = false)
    private String outcome;
    @Column(name = "ACTOR_ID", length = 100, nullable = false)
    private String actorId;
    @Column(name = "ACTOR_TYPE", length = 20, nullable = false)
    private String actorType;
    @Column(name = "REASON_CODE", length = 40)
    private String reasonCode;
    @Column(name = "BEFORE_HASH", length = 64)
    private String beforeHash;
    @Column(name = "AFTER_HASH", length = 64)
    private String afterHash;
    @Column(name = "CORRELATION_ID", length = 64, nullable = false)
    private String correlationId;
    @Column(name = "OCCURRED_AT", nullable = false)
    private OffsetDateTime occurredAt;

    public AuditLog(String id, String aggregateId, String action, String outcome, String actorId,
                    String actorType, String reasonCode, String beforeHash, String afterHash,
                    String correlationId) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.action = action;
        this.outcome = outcome;
        this.actorId = actorId;
        this.actorType = actorType;
        this.reasonCode = reasonCode;
        this.beforeHash = beforeHash;
        this.afterHash = afterHash;
        this.correlationId = correlationId;
        this.occurredAt = OffsetDateTime.now();
    }
}
