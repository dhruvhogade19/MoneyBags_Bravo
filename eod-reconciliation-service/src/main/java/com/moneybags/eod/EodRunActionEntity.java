package com.moneybags.eod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "EOD_RUN_ACTION")
class EodRunActionEntity {
    @Id
    @Column(name = "ACTION_ID", length = 36, nullable = false)
    private String id;

    @Column(name = "RUN_ID", length = 36, nullable = false)
    private String runId;

    @Column(name = "ACTION_TYPE", length = 40, nullable = false)
    private String actionType;

    @Column(name = "STEP_CODE", length = 60)
    private String stepCode;

    @Column(name = "REQUESTED_BY", length = 120, nullable = false)
    private String requestedBy;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "REQUEST_KIND", length = 40)
    private String requestKind;

    @Column(name = "REQUEST_KEY", length = 200)
    private String requestKey;

    @Column(name = "REQUEST_HASH", length = 64)
    private String requestHash;

    @Lob
    @Column(name = "DETAILS_JSON", nullable = false)
    private String detailsJson;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected EodRunActionEntity() {}

    EodRunActionEntity(String runId, String actionType, String stepCode, String requestedBy,
                       String reason, String detailsJson) {
        this(runId, actionType, stepCode, requestedBy, reason, detailsJson, null, null, null);
    }

    EodRunActionEntity(String runId, String actionType, String stepCode, String requestedBy,
                       String reason, String detailsJson, String requestKind, String requestKey,
                       String requestHash) {
        this.id = UUID.randomUUID().toString();
        this.runId = runId;
        this.actionType = actionType;
        this.stepCode = stepCode;
        this.requestedBy = requestedBy == null || requestedBy.isBlank() ? "SYSTEM" : requestedBy;
        this.reason = reason;
        // H2's Oracle mode treats all-null unique keys more strictly than Oracle. Give ordinary
        // audit events a private discriminator so the durable request-key constraint has identical
        // behavior in both databases without conflating them with API idempotency requests.
        this.requestKind = requestKind == null ? "AUDIT" : requestKind;
        this.requestKey = requestKey == null ? this.id : requestKey;
        this.requestHash = requestHash;
        this.detailsJson = detailsJson == null ? "{}" : detailsJson;
        this.createdAt = OffsetDateTime.now();
    }

    String runId() { return runId; }
    String actionType() { return actionType; }
    String stepCode() { return stepCode; }
    String requestedBy() { return requestedBy; }
    String reason() { return reason; }
    String detailsJson() { return detailsJson; }
    String requestKind() { return requestKind; }
    String requestKey() { return requestKey; }
    String requestHash() { return requestHash; }
}
